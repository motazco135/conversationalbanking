package io.github.motazco135.conversationalbanking.agents.card.dto;

/**
 * Customer-safe view of an active card used when the workflow must ask which card to act on.
 *
 * <p>Only masked, non-sensitive identifiers are exposed here — never the full PAN. The customer
 * (and the LLM) refer back to a card by its 1-based {@link #index()} or its {@link #maskedPan()}.
 */
public record CardSummary(
        int index,
        String maskedPan,
        String cardType
) {
    private static final String MASK_PREFIX = "**** ";

    /**
     * Build a masked representation from an internal vPan, exposing at most the last 4 characters.
     */
    public static String mask(String vPan) {
        if (vPan == null || vPan.isBlank()) {
            return MASK_PREFIX + "****";
        }
        String last4 = vPan.length() <= 4 ? vPan : vPan.substring(vPan.length() - 4);
        return MASK_PREFIX + last4;
    }
}
