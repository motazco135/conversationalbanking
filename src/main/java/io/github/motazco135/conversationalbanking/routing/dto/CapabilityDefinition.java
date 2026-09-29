package io.github.motazco135.conversationalbanking.routing.dto;

public record CapabilityDefinition(
        String domain,
        String capability,
        String description
) {
    public String key() {
        return domain + "__" + capability;
    }
}

