package io.github.motazco135.conversationalbanking.agents.complaint.dto;

import java.util.List;

/**
 * Structured directive returned by the {@code createComplaint} workflow tool.
 *
 * <p>The tool itself is the state machine for the complaint-creation lifecycle. Rather than relying
 * on ad-hoc chat coordination or unstructured LLM decisions, every invocation returns one of
 * {@link #WAITING_FOR_USER_INPUT}, {@link #COMPLETED} or {@link #FAILED}, together with the
 * information the caller needs to react deterministically ({@code ComplaintAgent}) and the
 * {@link #generationHint()} the LLM uses to phrase a natural message to the customer.
 *
 * <p>This mirrors the card-domain {@code CardWorkflowResult} contract (see ARCHITECTURE §6.4/§6.5).
 */
public record ComplaintWorkflowResult(
        String status,
        String requiredField,
        List<String> suggestedCategories,
        String generationHint,
        String complaintReferenceId
) {
    public static final String WAITING_FOR_USER_INPUT = "WAITING_FOR_USER_INPUT";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    /** The description of what happened is missing — ask the customer to explain the issue. */
    public static ComplaintWorkflowResult waitingForDescription() {
        return new ComplaintWorkflowResult(
                WAITING_FOR_USER_INPUT,
                "description",
                List.of(),
                "Ask the customer to briefly explain the issue or details of their complaint.",
                null);
    }

    /**
     * The customer has not explicitly confirmed the complaint specifics in the current turn (e.g. they
     * only said "yes"/"proceed" in a chained flow, or the description was inferred from earlier turns).
     * Gate creation and ask them to confirm what this complaint is actually about before filing.
     */
    public static ComplaintWorkflowResult waitingForDetailsConfirmation() {
        return new ComplaintWorkflowResult(
                WAITING_FOR_USER_INPUT,
                "description",
                List.of(),
                "Acknowledge that you are ready to file the complaint. If prior context mentions a "
                        + "stolen card or issue, ask whether this complaint is specifically about that "
                        + "incident or something else, and invite them to share specific details "
                        + "(e.g., transaction date, amount, or incident notes).",
                null);
    }

    /** The category could not be determined and must be chosen from the suggested list. */
    public static ComplaintWorkflowResult waitingForCategory(List<String> suggestedCategories) {
        return new ComplaintWorkflowResult(
                WAITING_FOR_USER_INPUT,
                "category",
                suggestedCategories,
                "Ask the customer which category best describes their complaint, presenting the "
                        + "suggested categories clearly.",
                null);
    }

    /** The complaint was created. */
    public static ComplaintWorkflowResult completed(String complaintReferenceId) {
        return new ComplaintWorkflowResult(
                COMPLETED,
                null,
                List.of(),
                "Confirm to the customer that their complaint has been logged. "
                        + "Reference: " + complaintReferenceId + ".",
                complaintReferenceId);
    }

    /** The operation could not be completed. */
    public static ComplaintWorkflowResult failed(String generationHint) {
        return new ComplaintWorkflowResult(FAILED, null, List.of(), generationHint, null);
    }

    public boolean isWaitingForUserInput() {
        return WAITING_FOR_USER_INPUT.equals(status);
    }

    public boolean isCompleted() {
        return COMPLETED.equals(status);
    }
}
