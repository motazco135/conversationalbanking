package io.github.motazco135.conversationalbanking.routing;

import io.github.motazco135.conversationalbanking.routing.dto.CapabilityDefinition;
import io.github.motazco135.conversationalbanking.routing.dto.RouteDecision;
import io.github.motazco135.conversationalbanking.routing.dto.RouterRequest;
import io.github.motazco135.conversationalbanking.routing.dto.RouterResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class CapabilityRouter {

    private final ChatClient openAiChatClient;
    private final CapabilityRegistry capabilityRegistry;
    private final RouterPromptFactory promptFactory;

    public CapabilityRouter(@Qualifier("openAiChatClient") ChatClient openAiChatClient, CapabilityRegistry capabilityRegistry,
                            RouterPromptFactory promptFactory) {
        this.openAiChatClient = openAiChatClient;
        this.capabilityRegistry = capabilityRegistry;
        this.promptFactory = promptFactory;
    }

    public RouterResult route(RouterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }

        List<CapabilityDefinition> enabledCapabilities = (request.availableCapabilities() != null && !request.availableCapabilities().isEmpty())
                ? request.availableCapabilities()
                : capabilityRegistry.getEnabledCapabilities();

        RouterRequest effectiveRequest = new RouterRequest(
                request.message(),
                request.context(),
                enabledCapabilities
        );

        log.info("Routing customer message with {} enabled capabilities", enabledCapabilities.size());
        long startTime = System.currentTimeMillis();
        try {

            String systemPrompt = promptFactory.systemPrompt();
            String userPrompt = promptFactory.userPrompt(effectiveRequest);

            RouterResult rawResult = openAiChatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(RouterResult.class);

            RouterResult validated = validate(rawResult, enabledCapabilities);
            long duration = System.currentTimeMillis() - startTime;
            log.info("Capability routing completed in {} ms. Unsupported: {}, Routes: {}",
                    duration, validated.unsupported(), validated.routes().size());
            return validated;

        }catch (Exception e){
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to route customer message after {} ms: {}", duration, e.getMessage(), e);
            throw new CapabilityRoutingException("Failed to determine capability route", e);
        }
    }

    public RouterResult validate(RouterResult rawResult, List<CapabilityDefinition> enabledCapabilities) {
        if (rawResult == null || rawResult.unsupported() || rawResult.routes() == null || rawResult.routes().isEmpty()) {
            return RouterResult.unsupportedResult();
        }

        Map<String, Set<String>> enabledMap = new HashMap<>();
        if (enabledCapabilities != null) {
            for (CapabilityDefinition cap : enabledCapabilities) {
                enabledMap.computeIfAbsent(cap.domain(), k -> new HashSet<>()).add(cap.capability());
            }
        }

        List<RouteDecision> validRoutes = new ArrayList<>();
        for (RouteDecision decision : rawResult.routes()) {
            if (decision == null || decision.domain() == null) {
                continue;
            }
            Set<String> validGoalsForDomain = enabledMap.get(decision.domain());
            if (validGoalsForDomain == null || validGoalsForDomain.isEmpty()) {
                continue;
            }

            List<String> filteredGoals = decision.goals() == null ? List.of() :
                    decision.goals().stream()
                            .filter(validGoalsForDomain::contains)
                            .distinct()
                            .toList();

            if (!filteredGoals.isEmpty()) {
                validRoutes.add(new RouteDecision(
                        decision.domain(),
                        filteredGoals,
                        decision.confidence()
                ));
            }
        }

        if (validRoutes.isEmpty()) {
            return RouterResult.unsupportedResult();
        }

        return new RouterResult(validRoutes, false,false, false);
    }

}
