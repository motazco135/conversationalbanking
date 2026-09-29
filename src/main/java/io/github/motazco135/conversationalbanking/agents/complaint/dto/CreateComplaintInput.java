package io.github.motazco135.conversationalbanking.agents.complaint.dto;

/**
 * LLM-supplied arguments for the {@code createComplaint} workflow tool.
 *
 * <p>All fields are optional. The tool itself drives the multi-turn workflow: when a required field
 * (currently {@link #description()}) is missing it returns a directive asking for it rather than
 * failing. {@link #category()} is optional — the tool defaults or infers a category when it is
 * omitted. {@link #relatedReference()} is a free-form pointer such as a masked PAN, card nickname,
 * or a transaction / reference ID the complaint concerns.
 *
 * <p>Security: {@code customerId} is intentionally NOT part of this contract. It is resolved from
 * the ambient execution context (see {@code ComplaintTools}) so the model can never file a complaint
 * against another customer's profile.
 *
 * <p>{@link #detailsConfirmed()} is the explicit confirmation gate for chained cross-domain flows
 * (e.g. BLOCK_CARD → CREATE_COMPLAINT). It must evaluate to {@code true} <em>only</em> when the
 * customer provided or explicitly confirmed the complaint specifics in the active conversational turn.
 * When the details were merely inferred from earlier turns (for example an earlier "my card was
 * stolen" phrase), it must remain {@code false}, so a bare "yes"/"proceed" acknowledgment cannot
 * trigger an immediate creation. A {@code null} value is treated as {@code false} (see
 * {@link #detailsConfirmed()}).
 */
public record CreateComplaintInput(
        String category,
        String description,
        String relatedReference,
        Boolean detailsConfirmed
) {
    /**
     * Whether the customer explicitly provided or confirmed the complaint specifics in the current
     * turn. Defaults to {@code false} when the model omits the flag (a {@code null} value), so the
     * absence of an explicit confirmation is never treated as consent.
     */
    @Override
    public Boolean detailsConfirmed() {
        return detailsConfirmed != null && detailsConfirmed;
    }
}
