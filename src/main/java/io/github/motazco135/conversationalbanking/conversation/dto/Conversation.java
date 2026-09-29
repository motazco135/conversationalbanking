package io.github.motazco135.conversationalbanking.conversation.dto;

import java.time.Instant;

public record Conversation(
        String conversationId,
        String customerId,
        ConversationStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
