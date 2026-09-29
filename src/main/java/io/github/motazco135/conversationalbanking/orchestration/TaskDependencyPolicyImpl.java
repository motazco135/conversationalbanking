package io.github.motazco135.conversationalbanking.orchestration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class TaskDependencyPolicyImpl  implements TaskDependencyPolicy{

    private static final String CAPABILITY_BLOCK_CARD = "BLOCK_CARD";
    private static final String CAPABILITY_REISSUE_CARD = "REISSUE_CARD";
    private static final String CAPABILITY_CREATE_COMPLAINT = "CREATE_COMPLAINT";
    private static final String CAPABILITY_GET_COMPLAINT = "GET_COMPLAINT";

    @Override
    public List<String> dependenciesFor(Task candidate, List<Task> allTasks) {
        if (candidate == null || allTasks == null || allTasks.isEmpty()) {
            return Collections.emptyList();
        }

        String capability = candidate.getCapability();
        if (capability == null) {
            return Collections.emptyList();
        }

        List<String> dependencies = new ArrayList<>();
        switch (capability.toUpperCase()) {
            case CAPABILITY_BLOCK_CARD:
            case CAPABILITY_GET_COMPLAINT:
                break;

            case CAPABILITY_REISSUE_CARD:
            case CAPABILITY_CREATE_COMPLAINT:
                // Depends on BLOCK_CARD only when BLOCK_CARD exists in the same plan
                for (Task task : allTasks) {
                    if (CAPABILITY_BLOCK_CARD.equalsIgnoreCase(task.getCapability())
                            && !task.getTaskId().equals(candidate.getTaskId())) {
                        dependencies.add(task.getTaskId());
                    }
                }
                break;

            default:
                log.debug("No specific dependency policy defined for capability: {}", capability);
                break;
        }

        log.info("Resolved dependencies for task {} ({}) -> {}",
                candidate.getTaskId(), candidate.getCapability(), dependencies);
        return Collections.unmodifiableList(dependencies);

    }
}
