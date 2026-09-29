package io.github.motazco135.conversationalbanking.routing;

import io.github.motazco135.conversationalbanking.routing.dto.CapabilityDefinition;
import io.github.motazco135.conversationalbanking.routing.dto.ConversationRoutingContext;
import io.github.motazco135.conversationalbanking.routing.dto.RouterRequest;
import org.springframework.stereotype.Component;

@Component
public class RouterPromptFactory {

    private static final String SYSTEM_PROMPT = """
            You are the capability router for a conversational banking application.

            Your task is to understand the customer's latest message and map it only to the supplied enabled banking capabilities.

            Rules:
            1. Select only from the supplied capability list.
            2. A message may require more than one capability.
            3. Do not invent domains or capabilities.
            4. In the output JSON, the domain field must contain ONLY the canonical domain name (e.g., "CARD_MANAGEMENT"), and the goals array must contain the capability names (e.g., ["BLOCK_CARD"]).
             Do not combine domain and capability into the domain field.
            5. Do not execute any action.
            6. Do not answer the customer.
            7. If nothing matches the enabled capabilities, return unsupported=true.
            8. Return only the required structured output.
            9. you should make sure that the order of the goal array is based on the criticality and security requirement (e.g, if customer say card stolen, create a complaint,
             we should make sure to BLOCK_CARD is more important than CREATE_COMPLAINT)  
            """.strip();

    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String userPrompt(RouterRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Enabled capabilities:\n\n");
        if (request.availableCapabilities() != null) {
            for (CapabilityDefinition cap : request.availableCapabilities()) {
                sb.append("- Domain: ").append(cap.domain()).append(" | Capability: ").append(cap.capability()).append("\n");
                if (cap.description() != null && !cap.description().isBlank()) {
                    sb.append("  Description: ").append(cap.description()).append("\n");
                }
                sb.append("\n");
            }
        }

        ConversationRoutingContext context = request.context();
        if (context != null) {
            boolean hasActiveDomain = context.activeDomain() != null && !context.activeDomain().isBlank();
            boolean hasPending = context.pendingCapabilities() != null && !context.pendingCapabilities().isEmpty();
            if (hasActiveDomain || hasPending) {
                sb.append("Routing context:\n");
                if (hasActiveDomain) {
                    sb.append("- Active domain: ").append(context.activeDomain()).append("\n");
                }
                if (hasPending) {
                    sb.append("- Pending capabilities: ").append(String.join(", ", context.pendingCapabilities())).append("\n");
                }
                sb.append("\n");
            }
        }

        sb.append("Customer message:\n");
        sb.append("\"").append(request.message() != null ? request.message() : "").append("\"");

        return sb.toString().strip();
    }

}
