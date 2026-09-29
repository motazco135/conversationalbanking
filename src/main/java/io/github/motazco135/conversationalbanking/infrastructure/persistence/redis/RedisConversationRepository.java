package io.github.motazco135.conversationalbanking.infrastructure.persistence.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.motazco135.conversationalbanking.conversation.dto.ConversationState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
public class RedisConversationRepository {

    private static final String KEY_PREFIX = "conversation:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final Duration ttl;
    private final ObjectMapper objectMapper;

    public RedisConversationRepository(
            RedisTemplate<String, Object> redisTemplate,
            @Value("${conversation.redis.ttl-seconds:86400}") long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void save(ConversationState state) {
        if (state == null || state.conversationId() == null) {
            return;
        }
        String key = key(state.conversationId());
        redisTemplate.opsForValue().set(key, state, ttl);
    }

    public Optional<ConversationState> findById(String conversationId) {
        if (conversationId == null) {
            return Optional.empty();
        }
        Object raw = redisTemplate.opsForValue().get(key(conversationId));
        if (raw == null) {
            return Optional.empty();
        }
        try {
            ConversationState state = objectMapper.convertValue(raw, ConversationState.class);
            return Optional.ofNullable(state);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public boolean existsById(String conversationId) {
        if (conversationId == null) {
            return false;
        }
        Boolean exists = redisTemplate.hasKey(key(conversationId));
        return Boolean.TRUE.equals(exists);
    }

    public void deleteById(String conversationId) {
        if (conversationId != null) {
            redisTemplate.delete(key(conversationId));
        }
    }

    private String key(String conversationId) {
        return KEY_PREFIX + conversationId;
    }
}

