package io.github.motazco135.conversationalbanking.routing.dto;

import java.util.List;

public record RouterRequest(
        String message,
        ConversationRoutingContext context,
        List<CapabilityDefinition> availableCapabilities
) {
    public RouterRequest {
        if (context == null) {
            context = ConversationRoutingContext.empty();
        }
        if (availableCapabilities == null) {
            availableCapabilities = List.of();
        }
    }
}
