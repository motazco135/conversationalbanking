package io.github.motazco135.conversationalbanking.orchestration.dto;

public enum TaskStatus {
    PENDING,
    READY,
    IN_PROGRESS,
    WAITING_INPUT,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    public boolean canTransitionTo(TaskStatus next) {
        if (this == next) {
            return true;
        }
        return switch (this) {
            case PENDING -> next == READY || next == CANCELLED;
            case READY -> next == IN_PROGRESS || next == WAITING_INPUT || next == COMPLETED || next == CANCELLED;
            case IN_PROGRESS -> next == WAITING_INPUT || next == COMPLETED || next == FAILED || next == CANCELLED;
            case WAITING_INPUT -> next == READY || next == CANCELLED;
            case COMPLETED, FAILED, CANCELLED -> false;
        };
    }
}
