package io.github.motazco135.conversationalbanking.routing;

public class CapabilityRoutingException extends RuntimeException {

    public CapabilityRoutingException(String message) {
        super(message);
    }

    public CapabilityRoutingException(String message, Throwable cause) {
        super(message, cause);
    }
}
