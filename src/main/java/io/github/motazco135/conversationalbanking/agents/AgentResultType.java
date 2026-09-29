package io.github.motazco135.conversationalbanking.agents;

public enum AgentResultType {
    COMPLETED,
    NEED_USER_INPUT,
    FAILED,
    REQUIRES_CONFIRMATION,
    /** The customer cancelled the current task or asked for another service; the coordinator re-routes. */
    INTENT_CHANGED
}

