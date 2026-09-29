package io.github.motazco135.conversationalbanking.agents.card;

import io.github.motazco135.conversationalbanking.agents.card.dto.CardRecord;
import io.github.motazco135.conversationalbanking.agents.card.dto.CardStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class CardService {

    public List<CardRecord> listActiveCardsForCustomer(String customerId) {
        List<CardRecord> activeCards = listMockCardRecords();
        return activeCards.stream().
                filter(card -> card.cardStatus() == CardStatus.ACTIVE && card.customerId().equals(customerId)).
                toList();
    }

    public CardRecord blockCard (String customerId, String vPan) {
        CardRecord blockCard  =  listMockCardRecords().stream().
                filter(card -> card.customerId().equals(customerId) &&
                        card.vPan().equals(vPan)).
                findFirst().
                map(card -> {
                    card.withCardStatus(CardStatus.BLOCKED);
                    return card;
                }).
                orElse(null);

        return blockCard;
    }

    public CardRecord issueNewDebitCard(String customerId) {
        return new CardRecord("12345678", "12345678", "1234", CardStatus.INACTIVE);
    }

    private List<CardRecord> listMockCardRecords() {
        return List.of(
                new CardRecord("123", "123", "1234", CardStatus.ACTIVE),
                new CardRecord("1234", "1234", "1234", CardStatus.INACTIVE),
                new CardRecord("12345", "12345", "1234", CardStatus.ACTIVE),
                new CardRecord("123456", "123456", "1234", CardStatus.BLOCKED),
                new CardRecord("1234567", "1234567", "1234", CardStatus.SUSPENDED)
        );
    }
}
