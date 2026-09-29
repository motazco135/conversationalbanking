package io.github.motazco135.conversationalbanking.agents.card;


import io.github.motazco135.conversationalbanking.agents.AgentRequest;
import io.github.motazco135.conversationalbanking.agents.AgentResult;
import io.github.motazco135.conversationalbanking.agents.AgentResultType;
import io.github.motazco135.conversationalbanking.agents.DomainAgent;
import io.github.motazco135.conversationalbanking.agents.card.dto.CardWorkflowResult;
import io.github.motazco135.conversationalbanking.conversation.dto.ConversationMessage;
import io.github.motazco135.conversationalbanking.orchestration.Task;
import io.github.motazco135.conversationalbanking.routing.CapabilityRegistry;
import io.github.motazco135.conversationalbanking.routing.dto.CapabilityDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CardAgent implements DomainAgent {

    public static final String DOMAIN = "CARD_MANAGEMENT";

    @Value("classpath:/prompts/system/card-agent-system-v2.st")
    private Resource systemPromptResource;

    @Value("classpath:/prompts/user/card-agent-user-v1.st")
    private Resource userPromptResource;

    private final ChatClient openAiChatClient;
    private final CardTools cardTools;
    private final CapabilityRegistry capabilityRegistry;


    public CardAgent(@Qualifier("openAiChatClient") ChatClient openAiChatClient,
                     CardService cardService, CardTools cardTools, CapabilityRegistry capabilityRegistry) {
        this.openAiChatClient = openAiChatClient;
        this.cardTools = cardTools;
        this.capabilityRegistry = capabilityRegistry;
    }


    private List<CapabilityDefinition> enabledCapabilityList(String domain){
        return capabilityRegistry.getEnabledCapabilities()
                .stream()
                .filter(capabilityDefinition ->capabilityDefinition.domain().equals(domain))
                .toList();
    }

    @Override
    public boolean supports(String domain) {
        return DOMAIN.equalsIgnoreCase(domain);
    }

    @Override
    public AgentResult handle(AgentRequest request) {
        Task task = request.task();
        String customerId = request.context().customerId();

        if (task == null || !supports(task.getDomain())) {
            return AgentResult.failed("Unsupported domain: " + (task != null ? task.getDomain() : "null"));
        }

        String capability = task.getCapability();
        if(request.context() == null || request.context().customerId() == null){
            return AgentResult.failed("Missing customer id");
        }
        log.info("CardAgent handling task {} capability {} for customer {}", task.getTaskId(), capability, customerId);

        List<Message> historyMessages = new ArrayList<>();
        StringBuilder collectedUserMessages = new StringBuilder();
        if (request.context().context().get("recentMessages") != null) {
            List<ConversationMessage> recentMessages = (List<ConversationMessage>) request.context().context().get("recentMessages");
            historyMessages = getChatHistoryMessages(recentMessages);
            if(!historyMessages.isEmpty()){
                historyMessages.forEach(message -> {
                    if(message.getMessageType().equals(MessageType.USER)){
                        collectedUserMessages.append("- ").append(message.getText()).append("\n");
                    }
                });
            }
        }

        // The latest user turn drives the agent. Without any history there is nothing to act on,
        // so fail fast with a clear result rather than letting getLast() throw.
        String currentUserMessage = latestUserMessage(historyMessages);
        if (currentUserMessage == null) {
            log.warn("CardAgent invoked with no user message in history for task {}", task.getTaskId());
            return AgentResult.failed("No customer message was available to process the card request.");
        }

        AgentResult rawResult;
        CardWorkflowResult workflowResult;
        try {
            cardTools.beginSession(customerId);
            rawResult = openAiChatClient.prompt()
                    .system(getSystemMessage(customerId,task).getText())
                    .messages(historyMessages)
                    .user(getUserMessage(customerId,collectedUserMessages.toString().strip(),currentUserMessage,task).getText())
                    .tools(cardTools)
                    //.options(OpenAiChatOptions.builder().reasoningEffort("minimal"))
                    .call()
                    .entity(AgentResult.class);
            workflowResult = cardTools.currentWorkflowResult();
        } finally {
            cardTools.endSession();
        }

        log.info("CardAgent Raw result: {} | workflow result: {}", rawResult, workflowResult);

        return mapResult(rawResult, workflowResult);
    }

    /**
     * The {@code blockCard} tool is the authority on the workflow <em>state</em>, so when it ran we
     * derive the {@link AgentResult} type (and any {@code requiredInput}) deterministically from its
     * {@link CardWorkflowResult} rather than trusting the LLM to pick them. The customer-facing
     * <em>message</em>, however, is always the model's natural-language {@code rawResult.message()}:
     * {@link CardWorkflowResult#generationHint()} is an internal directive that instructs the model how
     * to phrase its reply (e.g. "Inform the user multiple active cards were found…"), not text meant for
     * the customer. We fall back to the hint only when the model returned no usable message, so a blank
     * LLM response never leaves the customer with an empty reply. This applies to every terminal state
     * (waiting / completed / failed). When the tool did not run (e.g. the model asked for confirmation
     * first) the raw LLM result stands.
     */
    private AgentResult mapResult(AgentResult rawResult, CardWorkflowResult workflowResult) {
        if (workflowResult == null) {
            return rawResult;
        }

        String userFacingMessage = (rawResult != null && rawResult.message() != null && !rawResult.message().isBlank())
                ? rawResult.message()
                : workflowResult.generationHint();

        if (workflowResult.isWaitingForUserInput()) {
            Map<String, Object> output = new HashMap<>();
            output.put("requiredField", workflowResult.requiredField());
            output.put("availableCards", workflowResult.availableCards());
            output.put("generationHint", workflowResult.generationHint());
            return new AgentResult(AgentResultType.NEED_USER_INPUT, userFacingMessage, "CARD_SELECTION", output);
        }

        if (workflowResult.isCompleted()) {
            return AgentResult.completed(userFacingMessage,
                    Map.of("confirmationReference", workflowResult.confirmationReference()));
        }

        return AgentResult.failed(userFacingMessage);
    }

    /**
     * The current user turn to act on: the most recent {@link MessageType#USER} message, or the last
     * message overall if none is typed as USER. Returns {@code null} when history is empty so callers
     * can fail fast instead of dereferencing an absent element.
     */
    private String latestUserMessage(List<Message> historyMessages) {
        if (historyMessages == null || historyMessages.isEmpty()) {
            return null;
        }
        for (int i = historyMessages.size() - 1; i >= 0; i--) {
            Message message = historyMessages.get(i);
            if (message.getMessageType().equals(MessageType.USER)) {
                return message.getText();
            }
        }
        return historyMessages.getLast().getText();
    }

    private List<Message> getChatHistoryMessages(List<ConversationMessage> recentMessages) {
        List<Message> messages = new ArrayList<>();
        if(!recentMessages.isEmpty()){
            recentMessages.forEach(message -> {
                messages.add(message.toMessage());
            });
        }
        return  messages;
    }


    private Message getSystemMessage (String customerId,Task requierdTask){
        SystemPromptTemplate profilePromptTemplate = new SystemPromptTemplate(systemPromptResource);
        Message systemMessage = profilePromptTemplate.createMessage();
        return  systemMessage ;
    }

    private Message getUserMessage(String customerId, String collectedUserMessages, String userMessage, Task requierdTask){
        StringBuilder capabilityStringBuilder = new StringBuilder();
        for (CapabilityDefinition capabilityDefinition : enabledCapabilityList(requierdTask.getDomain())) {
            capabilityStringBuilder.append("- Domain: ").append(capabilityDefinition.domain()).append(" | Capability: ").append(capabilityDefinition.capability()).append("\n");
            if (capabilityDefinition.description() != null && !capabilityDefinition.description().isBlank()) {
                capabilityStringBuilder.append("  Description: ").append(capabilityDefinition.description()).append("\n");
            }
            capabilityStringBuilder.append("\n");
        }
        PromptTemplate template = new PromptTemplate(userPromptResource);
        Message message = template.createMessage(Map.of(
                "capability", capabilityStringBuilder.toString().strip(),
                "customerId", customerId,
                "taskId",requierdTask.getTaskId(),
                "taskDomain",requierdTask.getDomain(),
                "taskCapability",requierdTask.getCapability(),
                "taskConfidence",requierdTask.getConfidence(),
                "collectedUserMessages",collectedUserMessages,
                "userMessage",userMessage
        ));
        return  message;
    }
}
