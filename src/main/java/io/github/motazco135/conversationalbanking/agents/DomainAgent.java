package io.github.motazco135.conversationalbanking.agents;

public interface DomainAgent {

    boolean supports(String domain);

    AgentResult handle(AgentRequest request);
}
