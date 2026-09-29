package io.github.motazco135.conversationalbanking.agents.card.workflow;

import io.github.motazco135.conversationalbanking.agents.card.dto.CardSummary;

import java.util.List;

/**
 * The card-blocking lifecycle modeled as a Java 21 sealed hierarchy.
 *
 * <p>Each permitted record is one node of a deterministic state machine that
 * {@link CardWorkflowEngine} resolves from {@code (BlockCardInput, activeCards)} and then folds into a
 * {@code CardWorkflowResult} via an exhaustive {@code switch}. Modeling the states as a sealed type
 * makes the transition function total: the compiler guarantees every state is handled and rejects the
 * addition of a new state that is not.
 *
 * <ul>
 *   <li>{@link AwaitingCardSelection} — a selection is needed (multiple active cards and no selection
 *       yet, or an unresolvable/ambiguous selection). Carries only masked {@link CardSummary} views.</li>
 *   <li>{@link CardResolved} — a single card was resolved (the only active card, or an unambiguous
 *       selection); carries its internal {@code targetVPan} (never exposed to the customer or logged)
 *       so the executor can block it.</li>
 *   <li>{@link Failed} — the workflow cannot proceed (no active cards); carries an internal
 *       generation hint.</li>
 * </ul>
 */
public sealed interface CardState permits
        CardState.AwaitingCardSelection,
        CardState.CardResolved,
        CardState.Failed {

    record AwaitingCardSelection(List<CardSummary> availableCards) implements CardState {}

    record CardResolved(String targetVPan) implements CardState {}

    record Failed(String reason) implements CardState {}
}
