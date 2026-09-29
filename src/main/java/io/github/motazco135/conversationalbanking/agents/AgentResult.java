package io.github.motazco135.conversationalbanking.agents;

import java.util.Collections;
import java.util.Map;

public record AgentResult(
        AgentResultType type,
        String message,
        String requiredInput,
        Map<String, Object> output
) {
    public AgentResult {
        if (output == null) {
            output = Collections.emptyMap();
        }
    }

    public static AgentResult completed(String message, Map<String, Object> output) {
        return new AgentResult(AgentResultType.COMPLETED, message, null, output);
    }

    public static AgentResult needUserInput(String requiredInput, String message) {
        return new AgentResult(AgentResultType.NEED_USER_INPUT, message, requiredInput, Collections.emptyMap());
    }

    public static AgentResult failed(String message) {
        return new AgentResult(AgentResultType.FAILED, message, null, Collections.emptyMap());
    }
}
