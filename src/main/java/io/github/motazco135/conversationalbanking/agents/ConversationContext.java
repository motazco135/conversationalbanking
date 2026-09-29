package io.github.motazco135.conversationalbanking.agents;

import java.util.Collections;
import java.util.Map;

public record ConversationContext(
        String customerId,
        String conversationId,
        Map<String, Object> context
) {
    public ConversationContext {
        if (context == null) {
            context = Collections.emptyMap();
        }
    }
}
