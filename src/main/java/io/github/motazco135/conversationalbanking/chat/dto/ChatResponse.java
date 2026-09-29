package io.github.motazco135.conversationalbanking.chat.dto;

import io.github.motazco135.conversationalbanking.orchestration.Task;
import io.github.motazco135.conversationalbanking.routing.dto.RouterResult;

import java.util.List;

public record ChatResponse(
        String conversationId,
        String message,
        RouterResult routing,
        List<Task> tasks
        //OrchestrationResult orchestration
) {
    public ChatResponse(String conversationId, String message) {
        this(conversationId, message, null, List.of());
    }

    public ChatResponse(String conversationId,
                        String message,
                        RouterResult routing) {
        this(conversationId, message, routing, List.of());
    }

    public ChatResponse {
        if (tasks == null) {
            tasks = List.of();
        } else {
            tasks = List.copyOf(tasks);
        }
    }
}
