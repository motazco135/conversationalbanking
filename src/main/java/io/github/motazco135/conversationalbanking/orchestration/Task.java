package io.github.motazco135.conversationalbanking.orchestration;

import io.github.motazco135.conversationalbanking.orchestration.dto.TaskStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.*;


@Builder
@Setter
@Getter
public class Task {

    private String taskId;
    private String domain;
    private String capability;
    private TaskStatus status;
    private Map<String, Object> input;
    private Map<String, Object> output;
    private List<String> dependencies;
    private String waitingFor;
    private Double confidence;
    private Instant createdAt;
    private Instant updatedAt;

    public Task() {
        this.input = new HashMap<>();
        this.output = new HashMap<>();
        this.dependencies = new ArrayList<>();
        this.status = TaskStatus.PENDING;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Task(String taskId, String domain, String capability, TaskStatus status,
                Map<String, Object> input, Map<String, Object> output,
                List<String> dependencies, String waitingFor, double confidence,
                Instant createdAt, Instant updatedAt) {
        this.taskId = taskId;
        this.domain = domain;
        this.capability = capability;
        this.status = status != null ? status : TaskStatus.PENDING;
        this.input = input != null ? new HashMap<>(input) : new HashMap<>();
        this.output = output != null ? new HashMap<>(output) : new HashMap<>();
        this.dependencies = dependencies != null ? new ArrayList<>(dependencies) : new ArrayList<>();
        this.waitingFor = waitingFor;
        this.confidence = confidence;
        Instant now = Instant.now();
        this.createdAt = createdAt != null ? createdAt : now;
        this.updatedAt = updatedAt != null ? updatedAt : now;
    }

    public void transitionTo(TaskStatus newStatus) {
        Objects.requireNonNull(newStatus, "newStatus cannot be null");
        if (!this.status.canTransitionTo(newStatus)) {
            throw new OrchestrationException(String.format(
                    "Invalid task status transition for task %s (%s): %s -> %s",
                    taskId, capability, status, newStatus));
        }
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    public void putInput(String key, Object value) {
        this.input.put(key, value);
        this.updatedAt = Instant.now();
    }

    public void putOutput(String key, Object value) {
        this.output.put(key, value);
        this.updatedAt = Instant.now();
    }
}
