package io.github.motazco135.conversationalbanking.infrastructure.push;

import io.github.motazco135.conversationalbanking.infrastructure.push.dto.PushoverRequest;
import io.github.motazco135.conversationalbanking.infrastructure.push.dto.PushoverResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class PushOverService {

    private static final String DEFAULT_BASE_URL = "https://api.pushover.net/1";

    private final RestClient restClient;
    private final String apiKey;
    private final String userKey;


    public PushOverService(
            @Value("${pushover.api-key:}") String apiKey,
            @Value("${pushover.user-key:}") String userKey,
            RestClient.Builder restClientBuilder
    ) {
        this.apiKey = apiKey;
        this.userKey = userKey;
        this.restClient = (restClientBuilder != null ? restClientBuilder : RestClient.builder())
                .baseUrl(DEFAULT_BASE_URL)
                .build();
    }


    public PushoverResponse pushMessage(String message) {
        return pushMessage(message, null);
    }

    public PushoverResponse pushMessage(String message, String title) {
        return pushMessage(new PushoverRequest(this.apiKey, this.userKey, message, title));
    }

    public PushoverResponse pushMessage(PushoverRequest request) {
        String token = (request.token() != null && !request.token().isBlank()) ? request.token() : this.apiKey;
        String user = (request.user() != null && !request.user().isBlank()) ? request.user() : this.userKey;
        PushoverRequest payload = new PushoverRequest(token, user, request.message(), request.title());

        log.info("Sending message via Pushover: title='{}', user='{}'", payload.title(), payload.user());

        PushoverResponse response = restClient.post()
                .uri("/messages.json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(PushoverResponse.class);

        log.info("Pushover response: {}", response);
        return response;
    }
}
