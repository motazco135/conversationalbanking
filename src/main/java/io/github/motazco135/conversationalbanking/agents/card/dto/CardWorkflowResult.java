package io.github.motazco135.conversationalbanking.agents.card.dto;

import java.util.List;

/**
 * Structured directive returned by the {@code blockCard} workflow tool.
 *
 * <p>The tool itself is the state machine for the card-blocking lifecycle. Rather than relying on
 * ad-hoc chat coordination, every invocation returns one of {@link #WAITING_FOR_USER_INPUT},
 * {@link #COMPLETED} or {@link #FAILED}, together with the information the caller needs to react
 * deterministically ({@code CardAgent}) and the {@link #generationHint()} the LLM uses to phrase a
 * natural message to the customer.
 */
public record CardWorkflowResult(
        String status,
        String requiredField,
        List<CardSummary> availableCards,
        String generationHint,
        String confirmationReference
) {
    public static final String WAITING_FOR_USER_INPUT = "WAITING_FOR_USER_INPUT";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    /** Multiple candidate cards — ask the customer to choose before blocking anything. */
    public static CardWorkflowResult waitingForCardSelection(List<CardSummary> availableCards) {
        return new CardWorkflowResult(
                WAITING_FOR_USER_INPUT,
                "vPan",
                availableCards,
                "Inform the user multiple active cards were found. Present the options clearly by index "
                        + "and last 4 digits, and ask which one they want to block. Never invent or expose full card numbers.",
                null);
    }

    /** The card was blocked. */
    public static CardWorkflowResult completed(String confirmationReference, String maskedPan) {
        return new CardWorkflowResult(
                COMPLETED,
                null,
                List.of(),
                "Confirm to the customer that card " + maskedPan + " has been blocked. "
                        + "Reference: " + confirmationReference + ".",
                confirmationReference);
    }

    /** The operation could not be completed. */
    public static CardWorkflowResult failed(String generationHint) {
        return new CardWorkflowResult(FAILED, null, List.of(), generationHint, null);
    }

    public boolean isWaitingForUserInput() {
        return WAITING_FOR_USER_INPUT.equals(status);
    }

    public boolean isCompleted() {
        return COMPLETED.equals(status);
    }
}
