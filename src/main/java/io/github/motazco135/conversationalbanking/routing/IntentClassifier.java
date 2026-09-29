package io.github.motazco135.conversationalbanking.routing;

import io.github.motazco135.conversationalbanking.routing.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Score;
import org.springaicommunity.typesafe.question.SystemOneRequest;
import org.springaicommunity.typesafe.response.SystemOneResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class IntentClassifier {

    private static final double MIN_PROBABILITY = 0.40;

    private final TypeSafeClient typeSafeClient;

    public IntentClassifier(TypeSafeClient typeSafeClient) {
        this.typeSafeClient = typeSafeClient;
    }

    public RouterResult classify(RouterRequest routerRequest){
        var builder = SystemOneRequest.builder().state(routerRequest.message());
        for (var cap : routerRequest.availableCapabilities()) {
            builder.question(cap.key(), Noul.of("Is the customer asking for this: " + cap.description() + "?"));
        }
        builder.question("is_greeting", Noul.of("Is this only a greeting, with no banking request?"));
        builder.question("is_frustration", Score.builder()
                .instructions("How frustrated is the customer?")
                .level("Calm, just stating facts")
                .level("Frustrated but civil")
                .level("Very angry, strong language")
                .build());


        SystemOneResponse response = typeSafeClient.systemOne(builder.build());

        List<RouteDecision> routes = new ArrayList<>();
        for (CapabilityDefinition cap : routerRequest.availableCapabilities()) {
            double probability = response.noulValue(cap.key());
            if (probability >= MIN_PROBABILITY) {
                routes.add(new RouteDecision(cap.domain(),List.of(cap.capability()) ,probability));
            }
        }
        boolean isGreeting = response.noulValue("is_greeting") >= MIN_PROBABILITY;
        int exactLevel = response.score("is_frustration").nearestLevel();

        routes.sort((a, b) -> Double.compare(b.confidence(), a.confidence()));
        log.info("IntentClassifier2-routes: {}", routes);
        if (!routes.isEmpty()) {
            return new RouterResult(routes, false,false,false);
        }else if(isGreeting){
            return new RouterResult(routes, false,true,false);
        }else if(exactLevel >-1){
            return new RouterResult(routes, false,false,true);
        }
        return new RouterResult(routes, true,false,false);
    }
}
