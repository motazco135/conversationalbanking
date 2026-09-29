package io.github.motazco135.conversationalbanking.agents.card.dto;

/**
 * LLM-supplied arguments for the {@code blockCard} workflow tool.
 *
 * <p>All fields are optional on the first invocation: when the customer has a single active card
 * the tool auto-selects it. On a follow-up turn the LLM resolves the customer's selection into
 * either {@link #cardIndex()} (1-based, preferred) or {@link #vPan()}.
 *
 * <p>Security: {@code customerId} is intentionally NOT part of this contract. It is resolved from
 * the ambient execution context (see {@code CardTools}) so the model can never target another
 * customer's cards.
 */
public record BlockCardInput(
        String vPan,
        Integer cardIndex,
        String reason
) {
    public static final String DEFAULT_REASON = "LOST_OR_STOLEN";

    public BlockCardInput {
        if (reason == null || reason.isBlank()) {
            reason = DEFAULT_REASON;
        }
    }
}
