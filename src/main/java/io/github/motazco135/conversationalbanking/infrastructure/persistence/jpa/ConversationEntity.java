package io.github.motazco135.conversationalbanking.infrastructure.persistence.jpa;

import io.github.motazco135.conversationalbanking.conversation.dto.ConversationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "t_conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationEntity {
    @Id
    @Column(name = "conversation_id", nullable = false, length = 64)
    private String conversationId;

    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ConversationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

}
