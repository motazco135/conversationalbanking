package io.github.motazco135.conversationalbanking.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JpaConversationRepository extends JpaRepository<ConversationEntity, String> {
    List<ConversationEntity> findByCustomerId(String customerId);
}
