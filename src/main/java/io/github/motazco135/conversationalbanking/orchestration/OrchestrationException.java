package io.github.motazco135.conversationalbanking.orchestration;

public class OrchestrationException extends RuntimeException {

    public OrchestrationException(String message) {
        super(message);
    }

    public OrchestrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
