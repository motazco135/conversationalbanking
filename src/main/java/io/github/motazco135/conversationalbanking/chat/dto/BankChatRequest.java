package io.github.motazco135.conversationalbanking.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record BankChatRequest(
        String conversationId,

        @NotBlank(message = "customerId must not be blank")
        String customerId,

        @NotBlank(message = "message must not be blank")
        String message
) {}
