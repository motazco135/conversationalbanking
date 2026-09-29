package io.github.motazco135.conversationalbanking.routing.dto;

import java.util.List;

public record RouterResult(
        List<RouteDecision> routes,
        boolean unsupported,
        boolean isGreeting,
        boolean isFrustration
) {
    public RouterResult {
        if (routes == null) {
            routes = List.of();
        }
    }

    public static RouterResult unsupportedResult() {
        return new RouterResult(List.of(), true,false,false);
    }
}