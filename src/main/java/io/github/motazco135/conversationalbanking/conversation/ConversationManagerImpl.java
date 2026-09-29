package io.github.motazco135.conversationalbanking.conversation;

import io.github.motazco135.conversationalbanking.conversation.dto.Conversation;
import io.github.motazco135.conversationalbanking.conversation.dto.ConversationMessage;
import io.github.motazco135.conversationalbanking.conversation.dto.ConversationState;
import io.github.motazco135.conversationalbanking.conversation.dto.ConversationStatus;
import io.github.motazco135.conversationalbanking.infrastructure.persistence.jpa.ConversationEntity;
import io.github.motazco135.conversationalbanking.infrastructure.persistence.jpa.JpaConversationRepository;
import io.github.motazco135.conversationalbanking.infrastructure.persistence.redis.RedisConversationRepository;
import io.github.motazco135.conversationalbanking.orchestration.dto.TaskStatus;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class ConversationManagerImpl implements ConversationManager {

    private final JpaConversationRepository jpaConversationRepository;
    private final RedisConversationRepository redisConversationRepository;

    public ConversationManagerImpl(
            JpaConversationRepository jpaConversationRepository,
            RedisConversationRepository redisConversationRepository
    ) {
        this.jpaConversationRepository = jpaConversationRepository;
        this.redisConversationRepository = redisConversationRepository;
    }

    @Override
    @Transactional
    public ConversationState getOrCreate(String conversationId, String customerId) {
        if (conversationId != null && !conversationId.isBlank()) {
            Optional<ConversationState> cached = redisConversationRepository.findById(conversationId);
            if (cached.isPresent()) {
                return cached.get();
            }

            Optional<ConversationEntity> existingEntity = jpaConversationRepository.findById(conversationId);
            if (existingEntity.isPresent()) {
                ConversationEntity entity = existingEntity.get();
                ConversationState hydratedState = new ConversationState(
                        entity.getConversationId(),
                        entity.getCustomerId(),
                        entity.getStatus(),
                        List.of(),
                        Instant.now()
                );
                redisConversationRepository.save(hydratedState);
                return hydratedState;
            }
        }

        String effectiveId = (conversationId != null && !conversationId.isBlank())
                ? conversationId
                : UUID.randomUUID().toString();

        Instant now = Instant.now();
        ConversationEntity entity = ConversationEntity.builder()
                .conversationId(effectiveId)
                .customerId(customerId)
                .status(ConversationStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();

        jpaConversationRepository.save(entity);

        ConversationState newState = new ConversationState(
                effectiveId,
                customerId,
                ConversationStatus.ACTIVE,
                List.of(),
                now
        );

        redisConversationRepository.save(newState);
        return newState;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConversationState> getState(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Optional.empty();
        }

        Optional<ConversationState> cached = redisConversationRepository.findById(conversationId);
        if (cached.isPresent()) {
            return cached;
        }

        return jpaConversationRepository.findById(conversationId).map(entity -> {
            ConversationState state = new ConversationState(
                    entity.getConversationId(),
                    entity.getCustomerId(),
                    entity.getStatus(),
                    List.of(),
                    Instant.now()
            );
            redisConversationRepository.save(state);
            return state;
        });
    }

    @Override
    @Transactional
    public ConversationState appendMessage(String conversationId, ConversationMessage message) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId cannot be null or blank");
        }
        if (message == null) {
            throw new IllegalArgumentException("message cannot be null");
        }

        ConversationState currentState = getOrCreate(conversationId, null);
        ConversationState updatedState = currentState.withAppendedMessage(message);

        redisConversationRepository.save(updatedState);

        jpaConversationRepository.findById(conversationId).ifPresent(entity -> {
            entity.setUpdatedAt(Instant.now());
            jpaConversationRepository.save(entity);
        });

        return updatedState;
    }

    @Override
    @Transactional
    public ConversationState saveState(ConversationState state) {
        if (state == null || state.conversationId() == null || state.conversationId().isBlank()) {
            throw new IllegalArgumentException("state and conversationId cannot be null or blank");
        }
        redisConversationRepository.save(state);
        jpaConversationRepository.findById(state.conversationId()).ifPresent(entity -> {
            entity.setStatus(state.status());
            entity.setUpdatedAt(Instant.now());
            jpaConversationRepository.save(entity);
        });
        return state;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Conversation> getConversationMetadata(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Optional.empty();
        }

        return jpaConversationRepository.findById(conversationId).map(entity ->
                new Conversation(
                        entity.getConversationId(),
                        entity.getCustomerId(),
                        entity.getStatus(),
                        entity.getCreatedAt(),
                        entity.getUpdatedAt()
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getContext(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Map.of();
        }

        Optional<ConversationState> conversationStateOptional= getState(conversationId);
        if(conversationStateOptional.isEmpty()){
            return Map.of();
        }

        ConversationState state = conversationStateOptional.get();
        Map<String, Object> context = new HashMap<>();

        // 1. Find the current active/ready task from the task queue
        if (state.tasks() != null) {
            state.tasks().stream()
                    .filter(t -> TaskStatus.READY.equals(t.getStatus()) || TaskStatus.IN_PROGRESS.equals(t.getStatus()))
                    .findFirst()
                    .ifPresent(activeTask -> {
                        context.put("Tasks", activeTask.getTaskId());
                        context.put("domain", activeTask.getDomain());
                        context.put("capability", activeTask.getCapability());
                        context.put("TaskStatus", activeTask.getStatus());
                    });
        }

        // 2. Pass recent chat messages so the agent has multi-turn conversational memory
        if (state.recentMessages() != null) {
            context.put("recentMessages", state.recentMessages());
        }

        // 3. Merge any pre-existing context map data if present in Redis
        if (state.context() != null) {
            context.putAll(state.context());
        }
        return context;
    }
}

