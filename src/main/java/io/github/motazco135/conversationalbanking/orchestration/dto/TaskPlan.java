package io.github.motazco135.conversationalbanking.orchestration.dto;

import io.github.motazco135.conversationalbanking.orchestration.Task;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public record TaskPlan(List<Task> tasks) {

    public TaskPlan {
        tasks = tasks != null ? List.copyOf(tasks) : Collections.emptyList();
    }

    public Optional<Task> findById(String taskId) {
        if (taskId == null) {
            return Optional.empty();
        }
        return tasks.stream().filter(t -> taskId.equals(t.getTaskId())).findFirst();
    }

    public List<Task> readyTasks() {
        return tasks.stream().filter(t -> t.getStatus() == TaskStatus.READY).toList();
    }

    public Optional<Task> waitingInputTask() {
        return tasks.stream().filter(t -> t.getStatus() == TaskStatus.WAITING_INPUT).findFirst();
    }

    public boolean isComplete() {
        return !tasks.isEmpty() && tasks.stream().allMatch(t ->
                t.getStatus() == TaskStatus.COMPLETED || t.getStatus() == TaskStatus.CANCELLED);
    }

    public boolean hasWaitingInput() {
        return waitingInputTask().isPresent();
    }

}
