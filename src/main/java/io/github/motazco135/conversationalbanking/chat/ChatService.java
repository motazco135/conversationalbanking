package io.github.motazco135.conversationalbanking.chat;

import io.github.motazco135.conversationalbanking.agents.*;
import io.github.motazco135.conversationalbanking.chat.dto.BankChatRequest;
import io.github.motazco135.conversationalbanking.chat.dto.ChatResponse;
import io.github.motazco135.conversationalbanking.conversation.ConversationManager;
import io.github.motazco135.conversationalbanking.conversation.dto.ConversationMessage;
import io.github.motazco135.conversationalbanking.conversation.dto.ConversationState;
import io.github.motazco135.conversationalbanking.orchestration.Task;
import io.github.motazco135.conversationalbanking.orchestration.TaskPlanner;
import io.github.motazco135.conversationalbanking.orchestration.dto.TaskPlan;
import io.github.motazco135.conversationalbanking.orchestration.dto.TaskStatus;
import io.github.motazco135.conversationalbanking.routing.*;
import io.github.motazco135.conversationalbanking.routing.dto.ConversationRoutingContext;
import io.github.motazco135.conversationalbanking.routing.dto.RouterRequest;
import io.github.motazco135.conversationalbanking.routing.dto.RouterResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class ChatService {

    private static final String DEFAULT_RESPONSE = "Request understood";
    private static final String CARD_MANAGEMENT = "CARD_MANAGEMENT";
    private static final String COMPLAINT_MANAGEMENT = "COMPLAINTS";

    private final ConversationManager conversationManager;
    private final CapabilityRegistry capabilityRegistry;
    private final CapabilityRouter capabilityRouter;
    private final IntentClassifier intentClassifier;
    private final TaskPlanner taskPlanner;
    private final AgentRegistry agentRegistry;

    public ChatService(ConversationManager conversationManager,
                       CapabilityRegistry capabilityRegistry,
                       CapabilityRouter capabilityRouter,
                       IntentClassifier intentClassifier,
                       TaskPlanner taskPlanner,
                       AgentRegistry agentRegistry) {
        this.conversationManager = conversationManager;
        this.capabilityRegistry = capabilityRegistry;
        this.capabilityRouter = capabilityRouter;
        this.intentClassifier = intentClassifier;
        this.taskPlanner = taskPlanner;
        this.agentRegistry = agentRegistry;
    }

    public ChatResponse processMessage(BankChatRequest request) {
        validateRequest(request);
        String customerId =  request.customerId();

        // 1 & 2. Load or create conversation
        ConversationState state = conversationManager.getOrCreate(request.conversationId(), customerId);

        // Record user message
        ConversationMessage userMessage = ConversationMessage.user(
                UUID.randomUUID().toString(),
                request.message().trim()
        );
        state = state.withAppendedMessage(userMessage);
        conversationManager.appendMessage(state.conversationId(), userMessage);

        // 3. Check for WAITING_INPUT task
        Optional<Task> waitingTaskOpt = state.tasks().stream()
                .filter(t -> t.getStatus() == TaskStatus.WAITING_INPUT)
                .findFirst();

        // 4. Check for READY task
        Optional<Task> readyTaskOpt = state.tasks().stream()
                .filter(t -> t.getStatus() == TaskStatus.READY)
                .findFirst();

        String userText = request.message().trim();
        RouterResult routingResult = null;
        List<String> messages = new ArrayList<>();
        Optional<Task> activeTaskOpt = waitingTaskOpt.isPresent() ? waitingTaskOpt : readyTaskOpt;
        if (activeTaskOpt.isPresent()) {
            //TODO : validate user input based on the Agent request, most likely this will required another LLM call
            TaskExecution execution = executeTask(state, activeTaskOpt.get(), messages);
            state = execution.state();
            if (execution.agentResult().type() == AgentResultType.INTENT_CHANGED) {
                // The agent cancelled its task (handelAgentResponse); route what the customer asked for
                // instead, through the normal new-request flow — at most once per turn.
                RoutedRequest routed = routeNewRequest(state,
                        rerouteText(execution.agentResult(), userText), messages, true);
                state = routed.state();
                routingResult = routed.routingResult();
            }
        } else {
            log.info("Routing to Classification new message for conversation {}", state.conversationId());
            RoutedRequest routed = routeNewRequest(state, userText, messages, false);
            state = routed.state();
            routingResult = routed.routingResult();
        }

        String finalMessage = (!messages.isEmpty())
                ? String.join(" ", messages)
                : DEFAULT_RESPONSE;

        // Record assistant message
        ConversationMessage systemResponse = ConversationMessage.assistant(
                UUID.randomUUID().toString(),
                finalMessage
        );
        state = state.withAppendedMessage(systemResponse);
        conversationManager.appendMessage(state.conversationId(), systemResponse);

        return new ChatResponse(state.conversationId(), finalMessage, routingResult, state.tasks());
    }

    /**
     * Classify {@code text} and, if it maps to enabled capabilities, plan the tasks and run the first
     * READY one. After a cancellation ({@code afterCancellation}) a message with no banking route adds
     * nothing: the cancelling agent's acknowledgment is the whole reply.
     */
    private RoutedRequest routeNewRequest(ConversationState state, String text, List<String> messages,
                                          boolean afterCancellation) {
        RouterRequest routerRequest = new RouterRequest(
                text,
                routingContext(state),
                capabilityRegistry.getEnabledCapabilities()
        );
        RouterResult routingResult = intentClassifier.classify(routerRequest);
        boolean hasRoutes = !routingResult.unsupported() && !routingResult.routes().isEmpty();
        if (afterCancellation && !hasRoutes) {
            return new RoutedRequest(state, routingResult);
        }
        if(routingResult.isGreeting()){
            String hiMessage = "Hello," +state.customerId()+ ". Welcome to Saudi Awwal Bank Digital Assistant." +
                    " I can help you securely manage your accounts, cards, or complaints." +
                    " How can I assist you today?";
            messages.add(hiMessage);
        }else if(routingResult.unsupported()){
            messages.add("Sorry, I don't understand your request. Please try again.");
        }else if(routingResult.isFrustration()){
            messages.add("I understand you are frustrated, and I am truly sorry for the trouble this has caused you." +
                    " Your experience is important to us, and I want to make this right");
        }else{
            //plan tasks
            TaskPlan taskPlan = taskPlanner.createPlan(routingResult, state);
            state = state.withTasks(taskPlan.tasks());
            state = conversationManager.saveState(state);

            //execute frist ready task
            Task firstTask = findTask(state, TaskStatus.READY).orElseThrow(() ->
                    new IllegalStateException("Task planner did not create a READY task"));
            state = executeTask(state, firstTask, messages).state();
        }
        return new RoutedRequest(state, routingResult);
    }

    /** Run the agent for {@code task}, apply its result to the task, and add its reply to {@code messages}. */
    private TaskExecution executeTask(ConversationState state, Task task, List<String> messages) {
        //Change task status
        task.setStatus(TaskStatus.IN_PROGRESS);
        state = updateTask(state, task);

        AgentResult agentResult = executeAgent(state, task);
        state = handelAgentResponse(agentResult, state, task);
        messages.add(buildFinalResponse(state, agentResult));
        return new TaskExecution(state, agentResult);
    }

    /** What to route after an intent change: the agent's summary of the new request, else the raw message. */
    private String rerouteText(AgentResult agentResult, String userText) {
        Object requestedAction = agentResult.output().get("requestedAction");
        return (requestedAction instanceof String action && !action.isBlank()) ? action : userText;
    }

    private record TaskExecution(ConversationState state, AgentResult agentResult) {}

    private record RoutedRequest(ConversationState state, RouterResult routingResult) {}

    private ConversationRoutingContext routingContext(ConversationState state) {
        return ConversationRoutingContext.empty();
    }

    private void validateRequest(BankChatRequest request) {
        if (request == null) {
            throw new IllegalArgumentException( "Request is required");
        }

        if (request.customerId() == null || request.customerId().isBlank()) {
            throw new IllegalArgumentException("Customer ID is required");
        }

        if (request.message() == null|| request.message().isBlank()) {
            throw new IllegalArgumentException("Message is required");
        }
    }

    private ConversationState  updateTask(ConversationState state, Task task) {
        state = state.withUpdatedTask(task);
        return conversationManager.saveState(state);
    }

    private AgentResult executeAgent(ConversationState state, Task task) {
        ConversationContext context = new ConversationContext(state.customerId(),
                state.conversationId(),
                conversationManager.getContext(state.conversationId()));
        Optional<DomainAgent> agentOpt = agentRegistry.findAgent(task.getDomain());
        if (agentOpt.isPresent()) {
            DomainAgent agent = agentOpt.get();
            return agent.handle(new AgentRequest(task, context));
        }else {
            throw new IllegalArgumentException("Unsupported domain: " + task.getDomain());
        }
    }

    private ConversationState  handelAgentResponse(AgentResult agentResult, ConversationState state ,Task task){
        if(agentResult.type().equals(AgentResultType.NEED_USER_INPUT)){
            log.info("Agent Required input: {} for domain : {} and  Task id: {}",
                    agentResult.requiredInput(),
                    task.getDomain(),
                    task.getTaskId());

            task.setStatus(TaskStatus.WAITING_INPUT);
            task.setOutput(agentResult.output());
            state = updateTask(state, task);
        }else if(agentResult.type().equals(AgentResultType.COMPLETED)){
            task.setStatus(TaskStatus.COMPLETED);
            task.setOutput(agentResult.output());
            state = updateTask(state, task);

            //update next task staus to ready
            Optional<Task> pendingTaskOpt = findTask(state,TaskStatus.PENDING);
            if(pendingTaskOpt.isPresent()){
                Task pendingTask = pendingTaskOpt.get();
                pendingTask.setStatus(TaskStatus.READY);
                state = updateTask(state, pendingTask);
            }
        }else if(agentResult.type().equals(AgentResultType.INTENT_CHANGED)){
            log.info("Customer cancelled task {} ({}) or changed intent; requested action: {}",
                    task.getTaskId(), task.getCapability(), agentResult.output().get("requestedAction"));
            task.transitionTo(TaskStatus.CANCELLED);
            task.setOutput(agentResult.output());
            state = updateTask(state, task);
            state = cancelDependents(state, task.getTaskId());
        }
        return state;
    }

    /** Cancel every non-terminal task that (transitively) depends on the cancelled task. */
    private ConversationState cancelDependents(ConversationState state, String cancelledTaskId) {
        Set<String> cancelledIds = new HashSet<>(Set.of(cancelledTaskId));
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Task candidate : state.tasks()) {
                if (!candidate.getStatus().isTerminal()
                        && candidate.getDependencies().stream().anyMatch(cancelledIds::contains)) {
                    candidate.transitionTo(TaskStatus.CANCELLED);
                    state = updateTask(state, candidate);
                    cancelledIds.add(candidate.getTaskId());
                    log.info("Cancelled task {} ({}): it depended on a cancelled task",
                            candidate.getTaskId(), candidate.getCapability());
                    changed = true;
                }
            }
        }
        return state;
    }

    private String buildFinalResponse(ConversationState state, AgentResult agentResult) {
        if (agentResult == null) {
            return DEFAULT_RESPONSE;
        }
        String agentMessage = agentResult.message();

        /*
         * Agent requested input.
         * Nothing else should be added.
         */
        if (agentResult.type() == AgentResultType.NEED_USER_INPUT) {
            return agentMessage;
        }

        /*
         * Agent completed its task.
         * The COORDINATOR decides what happens next.
         */
        if (agentResult.type() == AgentResultType.COMPLETED) {
            Optional<Task> nextTask = findTask(state, TaskStatus.READY);
            if (nextTask.isPresent()) {
                return agentMessage + " " + buildNextTaskPrompt(nextTask.get());
            }
            return agentMessage;
        }

        return agentMessage != null ? agentMessage : DEFAULT_RESPONSE;
    }

    private String buildNextTaskPrompt(Task nextTask) {
        if (COMPLAINT_MANAGEMENT.equals(nextTask.getDomain())) {
            return "You also asked me to create a complaint. " + "Would you like me to continue?";
        }
        if (CARD_MANAGEMENT.equals(nextTask.getDomain())) {
            return "You also have another card request. " + "Would you like me to continue?";
        }
        return "You have another pending request. " + "Would you like me to continue?";
    }

    private Optional<Task> findTask(ConversationState state, TaskStatus status) {
        return state.tasks()
                .stream()
                .filter(task -> task.getStatus() == status)
                .findFirst();
    }
}
