package io.github.motazco135.conversationalbanking.orchestration;

import io.github.motazco135.conversationalbanking.conversation.dto.ConversationState;
import io.github.motazco135.conversationalbanking.orchestration.dto.TaskPlan;
import io.github.motazco135.conversationalbanking.routing.dto.RouterResult;

public interface TaskPlanner {
    TaskPlan createPlan(RouterResult routerResult, ConversationState conversationState);
}
