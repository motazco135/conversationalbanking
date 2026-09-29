package io.github.motazco135.conversationalbanking.agents.complaint.workflow;

import io.github.motazco135.conversationalbanking.agents.complaint.dto.ComplaintWorkflowResult;
import io.github.motazco135.conversationalbanking.agents.complaint.dto.CreateComplaintInput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Deterministic functional state-transition engine for the complaint-creation workflow.
 *
 * <p>The engine isolates all transition logic out of {@code ComplaintTools}: {@link #evaluate} is a
 * pure function of {@code (CreateComplaintInput, ComplaintActionExecutor)} whose only side effect is
 * the backend call it delegates to the injected {@link ComplaintActionExecutor} when the
 * {@link ComplaintState.ReadyToSubmit} terminal state is reached. It resolves the current
 * {@link ComplaintState} from the input, then folds it into a {@link ComplaintWorkflowResult} with an
 * <em>exhaustive</em> {@code switch} over the sealed hierarchy.
 *
 * <p>The {@link ComplaintState.NeedsConfirmation} branch is the chained-flow confirmation gate: a
 * complaint is persisted only when the customer both supplied a real account of what happened and
 * explicitly confirmed it in the current turn. A bare/generic acknowledgment ("yes", "proceed", …) or
 * an unconfirmed description never reaches {@link ComplaintState.ReadyToSubmit}. See ARCHITECTURE §6.5.
 */
@Slf4j
@Component
public class ComplaintWorkflowEngine {

    private static final String DEFAULT_CATEGORY = "GENERAL";

    /**
     * Bare acknowledgments that carry no complaint detail. When the description degenerates to one of
     * these (typically because the model echoed a "yes"/"proceed" turn in a chained flow), it must not
     * be accepted as an actual complaint description.
     */
    private static final Set<String> GENERIC_ACKNOWLEDGMENTS = Set.of(
            "yes", "y", "yeah", "yep", "yup", "sure", "ok", "okay", "k",
            "proceed", "start", "go", "go ahead", "continue", "do it", "please do",
            "please", "confirm", "confirmed", "correct", "right", "fine", "sounds good");

    /**
     * Resolve the current state and produce the next {@link ComplaintWorkflowResult}. The
     * {@code executor} is invoked exactly once, and only when the complaint is ready to submit.
     */
    public ComplaintWorkflowResult evaluate(
            CreateComplaintInput input,
            ComplaintActionExecutor executor) {

        ComplaintState state = resolveCurrentState(input);

        return switch (state) {
            case ComplaintState.NeedsDetails ignored -> ComplaintWorkflowResult.waitingForDescription();
            case ComplaintState.NeedsConfirmation ignored ->
                    ComplaintWorkflowResult.waitingForDetailsConfirmation();
            case ComplaintState.ReadyToSubmit ready -> {
                String reference = executor.executeCreate(
                        ready.category(), ready.description(), ready.relatedReference());
                if (reference == null || reference.isBlank()) {
                    log.warn("complaint workflow: create returned no reference");
                    yield ComplaintWorkflowResult.failed(
                            "Tell the customer the complaint could not be created and to try again shortly.");
                }
                yield ComplaintWorkflowResult.completed(reference);
            }
        };
    }

    /**
     * Map the input onto the state the machine is currently in.
     *
     * <ul>
     *   <li>No description → {@link ComplaintState.NeedsDetails}.</li>
     *   <li>Description present but generic, or not explicitly confirmed this turn →
     *       {@link ComplaintState.NeedsConfirmation} (the confirmation gate).</li>
     *   <li>Confirmed, real description → {@link ComplaintState.ReadyToSubmit} with a defaulted
     *       category.</li>
     * </ul>
     *
     * <p>Logs never include the free-text description.
     */
    private ComplaintState resolveCurrentState(CreateComplaintInput input) {
        String description = (input == null || input.description() == null)
                ? null : input.description().strip();
        if (description == null || description.isBlank()) {
            log.info("complaint workflow: description missing, requesting it");
            return new ComplaintState.NeedsDetails();
        }

        boolean generic = isGenericDescription(description);
        boolean confirmed = Boolean.TRUE.equals(input.detailsConfirmed());
        if (generic || !confirmed) {
            log.info("complaint workflow: gated (detailsConfirmed={}, genericDescription={}); "
                    + "requesting explicit details/confirmation", confirmed, generic);
            return new ComplaintState.NeedsConfirmation(generic, confirmed);
        }

        String category;
        if (input.category() == null || input.category().isBlank()) {
            category = DEFAULT_CATEGORY;
            log.info("complaint workflow: category defaulted to {}", DEFAULT_CATEGORY);
        } else {
            category = input.category().strip();
        }
        String relatedReference = (input.relatedReference() == null || input.relatedReference().isBlank())
                ? null
                : input.relatedReference().strip();

        return new ComplaintState.ReadyToSubmit(category, description, relatedReference);
    }

    /**
     * A description is "generic" — and therefore not a usable complaint account — when, after
     * normalization, it is only a bare acknowledgment such as "yes", "proceed", or "please do". Such a
     * value carries no information about what actually happened and must not be filed as a complaint.
     */
    private boolean isGenericDescription(String description) {
        String normalized = description.strip().toLowerCase().replaceAll("[.!,]+$", "").strip();
        return normalized.isBlank() || GENERIC_ACKNOWLEDGMENTS.contains(normalized);
    }

    /**
     * Executes the terminal backend action for a ready complaint and returns the reference id (or
     * {@code null}/blank if it could not be created). {@code ComplaintTools} supplies the
     * implementation, binding the ambient {@code customerId} and calling {@code ComplaintService}, so
     * the engine stays free of session and persistence concerns.
     */
    @FunctionalInterface
    public interface ComplaintActionExecutor {
        String executeCreate(String category, String description, String relatedReference);
    }
}
