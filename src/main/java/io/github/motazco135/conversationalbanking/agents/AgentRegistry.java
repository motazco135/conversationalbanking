package io.github.motazco135.conversationalbanking.agents;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class AgentRegistry {

    private final List<DomainAgent> agents;

    public AgentRegistry(List<DomainAgent> agents) {
        this.agents = agents != null ? agents : List.of();
    }

    public Optional<DomainAgent> findAgent(String domain) {
        if (domain == null) {
            return Optional.empty();
        }
        return agents.stream()
                .filter(agent -> agent.supports(domain))
                .findFirst();
    }

    public DomainAgent getAgent(String domain) {
        return findAgent(domain)
                .orElseThrow(() -> new IllegalArgumentException("No domain agent registered for domain: " + domain));
    }
}
