package io.github.motazco135.conversationalbanking.routing.dto;

import java.util.List;

public record ConversationRoutingContext(
        String activeDomain,
        List<String> pendingCapabilities
) {
    public ConversationRoutingContext {
        if (pendingCapabilities == null) {
            pendingCapabilities = List.of();
        }
    }

    public static ConversationRoutingContext empty() {
        return new ConversationRoutingContext(null, List.of());
    }
}
