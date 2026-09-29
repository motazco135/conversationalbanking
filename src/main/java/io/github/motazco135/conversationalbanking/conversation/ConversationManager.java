package io.github.motazco135.conversationalbanking.conversation;

import io.github.motazco135.conversationalbanking.conversation.dto.Conversation;
import io.github.motazco135.conversationalbanking.conversation.dto.ConversationMessage;
import io.github.motazco135.conversationalbanking.conversation.dto.ConversationState;

import java.util.Map;
import java.util.Optional;

public interface ConversationManager {

    ConversationState getOrCreate(String conversationId, String customerId);

    Optional<ConversationState> getState(String conversationId);

    ConversationState appendMessage(String conversationId, ConversationMessage message);

    ConversationState saveState(ConversationState state);

    Optional<Conversation> getConversationMetadata(String conversationId);

    Map<String, Object> getContext(String conversationId);
}

