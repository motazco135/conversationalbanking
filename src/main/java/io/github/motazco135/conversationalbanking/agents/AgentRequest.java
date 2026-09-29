package io.github.motazco135.conversationalbanking.agents;


import io.github.motazco135.conversationalbanking.orchestration.Task;

public record AgentRequest(
        Task task,
        ConversationContext context
) {}

