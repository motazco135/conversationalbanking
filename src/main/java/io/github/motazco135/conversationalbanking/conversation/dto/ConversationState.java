package io.github.motazco135.conversationalbanking.conversation.dto;

import io.github.motazco135.conversationalbanking.orchestration.Task;

import java.time.Instant;
import java.util.*;

public record ConversationState(
        String conversationId,
        String customerId,
        ConversationStatus status,
        List<ConversationMessage> recentMessages,
        List<Task> tasks,
        Map<String, Object> context,
        Instant lastActiveAt
) {
    public ConversationState {
        recentMessages = recentMessages != null ? List.copyOf(recentMessages) : List.of();
        tasks = tasks != null ? List.copyOf(tasks) : List.of();
        context = context != null ? Map.copyOf(context) : Map.of();
    }

    public ConversationState(
            String conversationId,
            String customerId,
            ConversationStatus status,
            List<ConversationMessage> recentMessages,
            Instant lastActiveAt
    ) {
        this(conversationId, customerId, status, recentMessages, List.of(), Map.of(), lastActiveAt);
    }

    public ConversationState withAppendedMessage(ConversationMessage message) {
        List<ConversationMessage> updated = new ArrayList<>(this.recentMessages);
        updated.add(message);
        return new ConversationState(
                this.conversationId,
                this.customerId,
                this.status,
                updated,
                this.tasks,
                this.context,
                Instant.now()
        );
    }

    public ConversationState withTasks(List<Task> newTasks) {
        return new ConversationState(
                this.conversationId,
                this.customerId,
                this.status,
                this.recentMessages,
                newTasks != null ? new ArrayList<>(newTasks) : List.of(),
                this.context,
                Instant.now()
        );
    }

    public ConversationState withUpdatedTask(Task updatedTask) {
        if (updatedTask == null) {
            return this;
        }
        List<Task> newTasks = new ArrayList<>();
        boolean found = false;
        for (Task t : this.tasks) {
            if (t.getTaskId().equals(updatedTask.getTaskId())) {
                newTasks.add(updatedTask);
                found = true;
            } else {
                newTasks.add(t);
            }
        }
        if (!found) {
            newTasks.add(updatedTask);
        }
        return new ConversationState(
                this.conversationId,
                this.customerId,
                this.status,
                this.recentMessages,
                newTasks,
                this.context,
                Instant.now()
        );
    }

    public ConversationState withContext(Map<String, Object> newContext) {
        return new ConversationState(
                this.conversationId,
                this.customerId,
                this.status,
                this.recentMessages,
                this.tasks,
                newContext != null ? new HashMap<>(newContext) : Map.of(),
                Instant.now()
        );
    }

    public ConversationState withContextEntry(String key, Object value) {
        Map<String, Object> updated = new HashMap<>(this.context);
        if (value != null) {
            updated.put(key, value);
        } else {
            updated.remove(key);
        }
        return new ConversationState(
                this.conversationId,
                this.customerId,
                this.status,
                this.recentMessages,
                this.tasks,
                updated,
                Instant.now()
        );
    }

    public Optional<Task> findTaskById(String taskId) {
        if (taskId == null) {
            return Optional.empty();
        }
        return tasks.stream().filter(t -> taskId.equals(t.getTaskId())).findFirst();
    }
}
