package io.github.motazco135.conversationalbanking.routing;

import io.github.motazco135.conversationalbanking.routing.dto.CapabilityDefinition;

import java.util.List;

public interface CapabilityRegistry {

    List<CapabilityDefinition> getEnabledCapabilities();
}
