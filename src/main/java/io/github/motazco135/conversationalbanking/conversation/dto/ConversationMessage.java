package io.github.motazco135.conversationalbanking.conversation.dto;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.Instant;

public record ConversationMessage(
        String messageId,
        String sender,
        String text,
        Instant timestamp
) {
    public static ConversationMessage user(String messageId, String text) {
        return new ConversationMessage(messageId, "USER", text, Instant.now());
    }

    public static ConversationMessage system(String messageId, String text) {
        return new ConversationMessage(messageId, "SYSTEM", text, Instant.now());
    }

    public static ConversationMessage assistant(String messageId, String text) {
        return new ConversationMessage(messageId, "ASSISTANT", text, Instant.now());
    }

    public Message toMessage(){
        return switch (sender) {
            case "USER" -> UserMessage.builder().text(text).build();
            case "ASSISTANT" -> AssistantMessage.builder().content(text).build();
            case "SYSTEM" -> SystemMessage.builder().text(text).build();
            default -> throw new IllegalArgumentException("Unknown sender: " + sender);
        };
    }
}

