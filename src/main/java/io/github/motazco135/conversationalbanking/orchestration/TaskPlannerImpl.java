package io.github.motazco135.conversationalbanking.orchestration;

import io.github.motazco135.conversationalbanking.conversation.dto.ConversationState;
import io.github.motazco135.conversationalbanking.orchestration.dto.TaskPlan;
import io.github.motazco135.conversationalbanking.orchestration.dto.TaskStatus;
import io.github.motazco135.conversationalbanking.routing.dto.RouteDecision;
import io.github.motazco135.conversationalbanking.routing.dto.RouterResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class TaskPlannerImpl implements TaskPlanner {

    private final TaskDependencyPolicy dependencyPolicy;

    public TaskPlannerImpl(TaskDependencyPolicy dependencyPolicy) {
        this.dependencyPolicy = dependencyPolicy;
    }

    @Override
    public TaskPlan createPlan(RouterResult routerResult, ConversationState conversationState) {
        if (routerResult == null || routerResult.unsupported() || routerResult.routes() == null || routerResult.routes().isEmpty()) {
            log.debug("RouterResult is null, unsupported, or empty. Returning empty TaskPlan.");
            return new TaskPlan(Collections.emptyList());
        }

        List<Task> rawTasks = new ArrayList<>();
        int taskCounter = 1;
        for (RouteDecision route : routerResult.routes()) {
            String domain = route.domain();
            List<String> goals = route.goals();
            if(goals == null || goals.isEmpty()){
                continue;
            }

            for(String goal : goals){
                String taskId = "task_" + taskCounter++;
                Task task = Task.builder()
                        .taskId(taskId)
                        .domain(domain)
                        .capability(goal)
                        .status(TaskStatus.PENDING)
                        .confidence(route.confidence())
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
                rawTasks.add(task);
            }
        }

        for (Task task : rawTasks) {
            List<String> dependencies = dependencyPolicy.dependenciesFor(task, rawTasks);
            task.setDependencies(dependencies);
            if (dependencies.isEmpty()) {
                task.setStatus(TaskStatus.READY);
            } else {
                task.setStatus(TaskStatus.PENDING);
            }
        }
        log.info("Created TaskPlan with {} tasks for conversation {}",
                rawTasks.size(), conversationState != null ? conversationState.conversationId() : "unknown");

        return new TaskPlan(rawTasks);
    }
}
