package io.github.motazco135.conversationalbanking.agents.card.dto;

public record CardRecord(String cardId,
                         String vPan,
                         String customerId,
                         CardStatus cardStatus) {

    public CardRecord withCardStatus(CardStatus cardStatus) {
        return new CardRecord(cardId, vPan, customerId, cardStatus);
    }
}
