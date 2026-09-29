package io.github.motazco135.conversationalbanking.orchestration;

import java.util.List;

public interface TaskDependencyPolicy {

    List<String> dependenciesFor(Task candidate, List<Task> allTasks);
}
