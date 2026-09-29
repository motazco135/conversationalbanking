package io.github.motazco135.conversationalbanking.agents.complaint.workflow;

/**
 * The complaint-creation lifecycle modeled as a Java 21 sealed hierarchy.
 *
 * <p>Each permitted record is one node of a deterministic state machine that
 * {@link ComplaintWorkflowEngine} resolves from a {@code CreateComplaintInput} and then folds into a
 * {@code ComplaintWorkflowResult} via an exhaustive {@code switch}. The sealed type makes the
 * transition function total: every state must be handled.
 *
 * <ul>
 *   <li>{@link NeedsDetails} — no usable description yet; ask the customer to explain the issue.</li>
 *   <li>{@link NeedsConfirmation} — a description exists but was not explicitly confirmed in the
 *       current turn, or is a bare acknowledgment; ask the customer to confirm the specifics before
 *       anything is filed. This is the chained-flow confirmation gate. Carries why the gate fired
 *       (never the free-text description, so the state is safe to log).</li>
 *   <li>{@link ReadyToSubmit} — a confirmed, detailed complaint ready to persist; carries the resolved
 *       category, description, and optional related reference.</li>
 * </ul>
 *
 * <p>There is no pre-execution failure state: a missing session is handled by {@code ComplaintTools},
 * and a failed persistence is mapped inside the {@link ReadyToSubmit} branch.
 */
public sealed interface ComplaintState permits
        ComplaintState.NeedsDetails,
        ComplaintState.NeedsConfirmation,
        ComplaintState.ReadyToSubmit {

    record NeedsDetails() implements ComplaintState {}

    record NeedsConfirmation(boolean genericDescription, boolean detailsConfirmed) implements ComplaintState {}

    record ReadyToSubmit(String category, String description, String relatedReference) implements ComplaintState {}
}
