package io.github.motazco135.conversationalbanking.routing.dto;

import java.util.List;

public record RouteDecision(
        String domain,
        List<String> goals,
        double confidence
) {
    public RouteDecision {
        if (goals == null) {
            goals = List.of();
        }
    }
}
