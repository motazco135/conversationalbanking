package io.github.motazco135.conversationalbanking.routing;

import io.github.motazco135.conversationalbanking.routing.dto.CapabilityDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InMemoryCapabilityRegistry implements CapabilityRegistry {

    private static final List<CapabilityDefinition> ENABLED_CAPABILITIES = List.of(
            new CapabilityDefinition(
                    "CARD_MANAGEMENT",
                    "BLOCK_CARD",
                    "Block, freeze, or stop a card — including when the customer says it is lost, stolen, or compromised."
            ),
            new CapabilityDefinition(
                    "CARD_MANAGEMENT",
                    "REISSUE_CARD",
                    "The customer explicitly asks for a new, replacement, or reissued card."
            ),
            new CapabilityDefinition(
                    "COMPLAINTS",
                    "CREATE_COMPLAINT",
                    "Create a new customer complaint."
            ),
            new CapabilityDefinition(
                    "COMPLAINTS",
                    "GET_COMPLAINT",
                    "Retrieve or follow up an existing complaint."
            )
    );

    @Override
    public List<CapabilityDefinition> getEnabledCapabilities() {
        return ENABLED_CAPABILITIES;
    }
}