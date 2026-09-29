package io.github.motazco135.conversationalbanking.agents.complaint;

import io.github.motazco135.conversationalbanking.agents.complaint.dto.ComplaintRecord;
import io.github.motazco135.conversationalbanking.agents.complaint.dto.ComplaintWorkflowResult;
import io.github.motazco135.conversationalbanking.agents.complaint.dto.CreateComplaintInput;
import io.github.motazco135.conversationalbanking.agents.complaint.workflow.ComplaintWorkflowEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Complaint-domain tools exposed to the LLM.
 *
 * <p>This class is a <strong>thin, stateless proxy</strong> between the Spring AI tool-callback
 * mechanism and the deterministic {@link ComplaintWorkflowEngine}. It owns only two concerns:
 * <ol>
 *   <li><b>Ambient session resolution</b> — the active {@code customerId} is never a tool argument;
 *       it is bound by {@code ComplaintAgent} via {@link #beginSession(String)} and read from a
 *       {@link ThreadLocal}, so the LLM cannot file a complaint against another customer.</li>
 *   <li><b>Backend execution</b> — it supplies a {@link ComplaintWorkflowEngine.ComplaintActionExecutor}
 *       that persists the complaint via {@code ComplaintService} once the engine reaches its terminal
 *       state.</li>
 * </ol>
 * All state-transition logic — including the chained-flow confirmation gate — lives in
 * {@link ComplaintWorkflowEngine}. {@code ComplaintAgent} reads {@link #currentWorkflowResult()} back
 * and maps it deterministically onto an {@code AgentResult}. See ARCHITECTURE §6.5.
 */
@Slf4j
@Component
public class ComplaintTools {

    private static final ThreadLocal<String> ACTIVE_CUSTOMER = new ThreadLocal<>();
    private static final ThreadLocal<ComplaintWorkflowResult> LAST_WORKFLOW_RESULT = new ThreadLocal<>();

    private final ComplaintService complaintService;
    private final ComplaintWorkflowEngine complaintWorkflowEngine;

    public ComplaintTools(ComplaintService complaintService, ComplaintWorkflowEngine complaintWorkflowEngine) {
        this.complaintService = complaintService;
        this.complaintWorkflowEngine = complaintWorkflowEngine;
    }

    // --- Ambient session management (called by ComplaintAgent, not the LLM) -------------------

    /** Bind the customer whose complaints the tools may act on for the current request thread. */
    public void beginSession(String customerId) {
        ACTIVE_CUSTOMER.set(customerId);
        LAST_WORKFLOW_RESULT.remove();
    }

    /** The last {@link ComplaintWorkflowResult} produced by {@link #createComplaint} on this thread, or null. */
    public ComplaintWorkflowResult currentWorkflowResult() {
        return LAST_WORKFLOW_RESULT.get();
    }

    /** Clear all thread-bound state. Must be called in a {@code finally} block after the LLM call. */
    public void endSession() {
        ACTIVE_CUSTOMER.remove();
        LAST_WORKFLOW_RESULT.remove();
    }

    // --- Tools ------------------------------------------------------------------------------

    @Tool(description = """
            Manage the end-to-end complaint-creation workflow. This is the single entry point for
            filing a complaint. Pass the customer's own description of what happened; category and
            relatedReference are optional. Set detailsConfirmed = true ONLY when the customer has
            provided or explicitly confirmed the specific complaint details in the CURRENT turn; when
            they merely acknowledged ("yes"/"proceed") or the details were inferred from earlier turns,
            set detailsConfirmed = false. If the description is missing or generic, or detailsConfirmed
            is false, the tool returns WAITING_FOR_USER_INPUT asking you to collect and confirm the
            specifics — follow the returned generationHint. When a confirmed, detailed description is
            present the complaint is created and COMPLETED with a reference id. Never invent a
            description, never summarize; pass the customer's own words through.""")
    public ComplaintWorkflowResult createComplaint(CreateComplaintInput input) {
        String customerId = ACTIVE_CUSTOMER.get();
        if (customerId == null || customerId.isBlank()) {
            log.warn("createComplaint invoked without an active customer session");
            return record(ComplaintWorkflowResult.failed(
                    "The complaint session is not available. Ask the customer to try again."));
        }

        CreateComplaintInput safeInput = input != null
                ? input
                : new CreateComplaintInput(null, null, null, false);

        // Delegate all state transitions (including the confirmation gate) to the engine; the executor
        // performs the terminal backend persistence and returns the reference id.
        ComplaintWorkflowResult result = complaintWorkflowEngine.evaluate(safeInput,
                (category, description, relatedReference) ->
                        executeCreate(customerId, category, description, relatedReference));
        return record(result);
    }

    @Tool(description = "Look up an existing complaint for the current customer by its reference id.")
    public String getComplaint(String complaintId) {
        String customerId = ACTIVE_CUSTOMER.get();
        if (customerId == null || customerId.isBlank()) {
            return "FAILURE: no active customer session.";
        }
        if (complaintId == null || complaintId.isBlank()) {
            return "FAILURE: a complaint reference id is required. Ask the customer for it.";
        }
        ComplaintRecord complaintRecord;
        try {
            complaintRecord = complaintService.getComplaint(customerId, complaintId);
        } catch (IllegalArgumentException e) {
            log.warn("getComplaint invalid arguments for customer {}: {}", customerId, e.getMessage());
            return "FAILURE: the complaint reference id is invalid. Ask the customer to confirm it.";
        }
        if (complaintRecord != null) {
            return "SUCCESS: Complaint found for customer: " + complaintRecord.customerId() +
                    " with Complaint Id: " + complaintRecord.complaintId() +
                    " with Complaint Status: " + complaintRecord.complaintStatus() +
                    " Complaint Details: " + complaintRecord.complaintDetails();
        }
        return "FAILURE: Complaint not found";
    }

    // --- Backend execution & helpers --------------------------------------------------------

    /**
     * Terminal backend action handed to the engine: persist the complaint and return its reference id,
     * or {@code null} when creation failed (the engine maps that to FAILED).
     */
    private String executeCreate(String customerId, String category, String description, String relatedReference) {
        ComplaintRecord complaintRecord =
                complaintService.createComplaint(customerId, category, description, relatedReference);
        if (complaintRecord == null || complaintRecord.complaintId() == null) {
            return null;
        }
        log.info("createComplaint completed for customer {} reference {}", customerId, complaintRecord.complaintId());
        return complaintRecord.complaintId();
    }

    private ComplaintWorkflowResult record(ComplaintWorkflowResult result) {
        LAST_WORKFLOW_RESULT.set(result);
        return result;
    }
}
