package io.github.motazco135.conversationalbanking.agents.card;

import io.github.motazco135.conversationalbanking.agents.card.dto.BlockCardInput;
import io.github.motazco135.conversationalbanking.agents.card.dto.CardRecord;
import io.github.motazco135.conversationalbanking.agents.card.dto.CardSummary;
import io.github.motazco135.conversationalbanking.agents.card.dto.CardWorkflowResult;
import io.github.motazco135.conversationalbanking.agents.card.workflow.CardWorkflowEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Card-domain tools exposed to the LLM.
 *
 * <p>This class is a <strong>thin, stateless proxy</strong> between the Spring AI tool-callback
 * mechanism and the deterministic {@link CardWorkflowEngine}. It owns only two concerns:
 * <ol>
 *   <li><b>Ambient session resolution</b> — the active {@code customerId} is never a tool argument;
 *       it is bound by {@code CardAgent} via {@link #beginSession(String)} and read from a
 *       {@link ThreadLocal}, so the LLM cannot target another customer's cards.</li>
 *   <li><b>Backend execution</b> — it queries {@code CardService} for the live active-card list and
 *       supplies a {@link CardWorkflowEngine.CardActionExecutor} that performs the actual block.</li>
 * </ol>
 * All state-transition logic (auto-block vs. ask, selection resolution, terminal mapping) lives in
 * {@link CardWorkflowEngine}. {@code CardAgent} reads {@link #currentWorkflowResult()} back and maps
 * it deterministically onto an {@code AgentResult}. See ARCHITECTURE §6.4.
 */
@Slf4j
@Component
public class CardTools {

    private static final ThreadLocal<String> ACTIVE_CUSTOMER = new ThreadLocal<>();
    private static final ThreadLocal<CardWorkflowResult> LAST_WORKFLOW_RESULT = new ThreadLocal<>();

    private final CardService cardService;
    private final CardWorkflowEngine cardWorkflowEngine;

    public CardTools(CardService cardService, CardWorkflowEngine cardWorkflowEngine) {
        this.cardService = cardService;
        this.cardWorkflowEngine = cardWorkflowEngine;
    }

    // --- Ambient session management (called by CardAgent, not the LLM) -----------------------

    /** Bind the customer whose cards the tools may act on for the current request thread. */
    public void beginSession(String customerId) {
        ACTIVE_CUSTOMER.set(customerId);
        LAST_WORKFLOW_RESULT.remove();
    }

    /** The last {@link CardWorkflowResult} produced by {@link #blockCard} on this thread, or null. */
    public CardWorkflowResult currentWorkflowResult() {
        return LAST_WORKFLOW_RESULT.get();
    }

    /** Clear all thread-bound state. Must be called in a {@code finally} block after the LLM call. */
    public void endSession() {
        ACTIVE_CUSTOMER.remove();
        LAST_WORKFLOW_RESULT.remove();
    }

    // --- Tools ------------------------------------------------------------------------------

    @Tool(description = """
            Manage the end-to-end card-blocking workflow. Call with no target on the first turn:
            if the customer has a single active card it is blocked immediately; if several are active
            the result asks which one to block. On the follow-up turn, pass the customer's choice as
            cardIndex (1-based, from the presented list) or vPan to complete the block. Do not invent
            card numbers and do not call this tool until the customer clearly intends to block a card.""")
    public CardWorkflowResult blockCard(BlockCardInput input) {
        String customerId = ACTIVE_CUSTOMER.get();
        if (customerId == null || customerId.isBlank()) {
            log.warn("blockCard invoked without an active customer session");
            return record(CardWorkflowResult.failed(
                    "The card-blocking session is not available. Ask the customer to try again."));
        }

        BlockCardInput safeInput = input != null ? input : new BlockCardInput(null, null, null);
        List<CardRecord> activeCards = cardService.listActiveCardsForCustomer(customerId);
        // Never log raw vPan values (PCI data-minimization) — only how the selection was expressed.
        log.info("blockCard workflow for customer {}: {} active card(s), selection={}",
                customerId, activeCards.size(), describeSelection(safeInput));

        // Delegate all state transitions to the engine; the executor performs the terminal backend
        // block and mints the confirmation reference, keeping the engine free of session concerns.
        CardWorkflowResult result = cardWorkflowEngine.evaluate(safeInput, activeCards,
                vPan -> executeBlock(customerId, vPan));
        return record(result);
    }

    @Tool(description = "Issue a new debit card for the current customer.")
    public String issueNewDebitCard() {
        String customerId = ACTIVE_CUSTOMER.get();
        if (customerId == null || customerId.isBlank()) {
            return "Failed: no active customer session.";
        }
        CardRecord cardRecord = cardService.issueNewDebitCard(customerId);
        if (cardRecord == null) {
            log.info("Failed to issue a new debit card for Customer : {}", customerId);
            return "Failed to issue a new debit card for Customer :" + customerId;
        }
        log.info("SUCCESS to issue a new debit card for Customer : {}", customerId);
        return "SUCCESS: A new debit card " + CardSummary.mask(cardRecord.vPan())
                + " has been successfully issued.";
    }

    // --- Backend execution & helpers --------------------------------------------------------

    /**
     * Terminal backend action handed to the engine: block the resolved card and return a confirmation
     * reference, or {@code null} when the block could not be performed (the engine maps that to FAILED).
     */
    private String executeBlock(String customerId, String vPan) {
        CardRecord blocked = cardService.blockCard(customerId, vPan);
        if (blocked == null) {
            return null;
        }
        String reference = "BLK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("blockCard completed for customer {} vPan {} reference {}",
                customerId, CardSummary.mask(vPan), reference);
        return reference;
    }

    /** Describe how a selection was expressed, without ever exposing raw card identifiers. */
    private String describeSelection(BlockCardInput input) {
        if (input.cardIndex() != null) {
            return "byIndex(" + input.cardIndex() + ")";
        }
        if (input.vPan() != null && !input.vPan().isBlank()) {
            return "byIdentifier";
        }
        return "none";
    }

    private CardWorkflowResult record(CardWorkflowResult result) {
        LAST_WORKFLOW_RESULT.set(result);
        return result;
    }
}
