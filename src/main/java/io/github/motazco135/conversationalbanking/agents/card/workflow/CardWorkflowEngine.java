package io.github.motazco135.conversationalbanking.agents.card.workflow;

import io.github.motazco135.conversationalbanking.agents.card.dto.BlockCardInput;
import io.github.motazco135.conversationalbanking.agents.card.dto.CardRecord;
import io.github.motazco135.conversationalbanking.agents.card.dto.CardSummary;
import io.github.motazco135.conversationalbanking.agents.card.dto.CardWorkflowResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Deterministic functional state-transition engine for the card-blocking workflow.
 *
 * <p>The engine isolates all transition logic out of {@code CardTools}: {@link #evaluate} is a pure
 * function of {@code (BlockCardInput, activeCards, CardActionExecutor)} whose only side effect is the
 * backend call it delegates to the injected {@link CardActionExecutor} when a terminal action is
 * reached. It first resolves the current {@link CardState} from the input and the customer's live
 * active-card list, then folds that state into a {@link CardWorkflowResult} with an <em>exhaustive</em>
 * {@code switch} over the sealed hierarchy — the compiler enforces totality.
 *
 * <p>Security note: the engine resolves selections against full {@link CardRecord}s (so it can obtain
 * the internal {@code vPan} needed to block a card) but only ever surfaces masked {@link CardSummary}
 * views to the caller. Full PANs never leave this layer.
 */
@Slf4j
@Component
public class CardWorkflowEngine {

    private static final String CARD_TYPE = "Card";

    /**
     * Resolve the current state and produce the next {@link CardWorkflowResult}. The
     * {@code executor} is invoked exactly once, and only when a single card has been resolved and is
     * ready to block.
     */
    public CardWorkflowResult evaluate(
            BlockCardInput input,
            List<CardRecord> activeCards,
            CardActionExecutor executor) {

        CardState state = resolveCurrentState(input, activeCards);

        return switch (state) {
            case CardState.CardResolved resolved -> executeBlock(resolved.targetVPan(), executor);
            case CardState.AwaitingCardSelection awaiting ->
                    CardWorkflowResult.waitingForCardSelection(awaiting.availableCards());
            case CardState.Failed failed -> CardWorkflowResult.failed(failed.reason());
        };
    }

    /**
     * Map {@code (input, activeCards)} onto the state the machine is currently in. All transition
     * decisions are made here; {@link #evaluate} only folds the resulting state.
     *
     * <ul>
     *   <li>No active cards → {@link CardState.Failed}.</li>
     *   <li>No selection and a single active card → {@link CardState.CardResolved} (auto-block).</li>
     *   <li>No selection and several active cards → {@link CardState.AwaitingCardSelection}.</li>
     *   <li>A selection matching exactly one card → {@link CardState.CardResolved}; zero or an
     *       ambiguous (e.g. shared last-4) match → {@link CardState.AwaitingCardSelection} presenting
     *       the full active list so 1-based indices stay stable across turns.</li>
     * </ul>
     *
     * <p>Logs never include raw vPans or state records ({@code CardResolved} holds the raw vPan).
     */
    private CardState resolveCurrentState(BlockCardInput input, List<CardRecord> activeCards) {
        if (activeCards == null || activeCards.isEmpty()) {
            log.info("card workflow: no active cards to block");
            return new CardState.Failed(
                    "Tell the customer no active cards were found on their profile to block.");
        }

        boolean hasSelection = input != null
                && (input.cardIndex() != null || (input.vPan() != null && !input.vPan().isBlank()));
        if (!hasSelection) {
            if (activeCards.size() == 1) {
                CardRecord only = activeCards.getFirst();
                log.info("card workflow: single active card {} auto-selected", CardSummary.mask(only.vPan()));
                return new CardState.CardResolved(only.vPan());
            }
            log.info("card workflow: {} active cards and no selection; asking which one", activeCards.size());
            return new CardState.AwaitingCardSelection(toSummaries(activeCards));
        }

        List<CardRecord> matches = resolveMatches(activeCards, input);
        if (matches.size() == 1) {
            log.info("card workflow: selection resolved to card {}", CardSummary.mask(matches.getFirst().vPan()));
            return new CardState.CardResolved(matches.getFirst().vPan());
        }
        // Zero matches (selection did not resolve) or more than one (ambiguous) → never auto-pick.
        if (matches.isEmpty()) {
            log.info("card workflow: selection matched no active card; asking again");
        } else {
            log.info("card workflow: selection ambiguous ({} matches); asking for confirmation", matches.size());
        }
        return new CardState.AwaitingCardSelection(toSummaries(activeCards));
    }

    private CardWorkflowResult executeBlock(String vPan, CardActionExecutor executor) {
        String reference = executor.executeBlock(vPan);
        if (reference == null || reference.isBlank()) {
            log.warn("card workflow: block of card {} returned no reference", CardSummary.mask(vPan));
            return CardWorkflowResult.failed(
                    "Tell the customer the card could not be blocked and to try again shortly.");
        }
        return CardWorkflowResult.completed(reference, CardSummary.mask(vPan));
    }

    /**
     * Resolve the LLM-supplied selection to the candidate card(s) it identifies. A 1-based
     * {@code cardIndex} and an exact {@code vPan} each identify at most one card; a last-4 quote may
     * match several, so all matches are returned and the caller decides whether the selection is
     * unambiguous enough to act on.
     */
    private List<CardRecord> resolveMatches(List<CardRecord> activeCards, BlockCardInput input) {
        if (input.cardIndex() != null) {
            int idx = input.cardIndex();
            if (idx >= 1 && idx <= activeCards.size()) {
                return List.of(activeCards.get(idx - 1));
            }
            return List.of();
        }
        if (input.vPan() != null && !input.vPan().isBlank()) {
            String requested = input.vPan().trim();
            // An exact PAN is unique — prefer it over a possibly-ambiguous last-4 match.
            CardRecord exact = activeCards.stream()
                    .filter(card -> requested.equals(card.vPan()))
                    .findFirst()
                    .orElse(null);
            if (exact != null) {
                return List.of(exact);
            }
            return activeCards.stream()
                    .filter(card -> matchesVPan(card.vPan(), requested))
                    .toList();
        }
        return List.of();
    }

    /** Match either the exact vPan or the last-4 digits the customer may have quoted. */
    private boolean matchesVPan(String cardVPan, String requested) {
        if (cardVPan == null) {
            return false;
        }
        if (cardVPan.equals(requested)) {
            return true;
        }
        String cardLast4 = cardVPan.length() <= 4 ? cardVPan : cardVPan.substring(cardVPan.length() - 4);
        return cardLast4.equals(requested);
    }

    private List<CardSummary> toSummaries(List<CardRecord> activeCards) {
        return IntStream.range(0, activeCards.size())
                .mapToObj(i -> new CardSummary(
                        i + 1,
                        CardSummary.mask(activeCards.get(i).vPan()),
                        CARD_TYPE))
                .toList();
    }

    /**
     * Executes the terminal backend action for a resolved card and returns the confirmation
     * reference (or {@code null}/blank if the block could not be performed). {@code CardTools}
     * supplies the implementation, binding the ambient {@code customerId} and calling
     * {@code CardService}, so the engine stays free of session and persistence concerns.
     */
    @FunctionalInterface
    public interface CardActionExecutor {
        String executeBlock(String vPan);
    }
}
