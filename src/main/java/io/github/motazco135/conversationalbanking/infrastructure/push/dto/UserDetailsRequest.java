package io.github.motazco135.conversationalbanking.infrastructure.push.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record UserDetailsRequest(
        @JsonProperty(required = false)
        @JsonPropertyDescription("The user's name, if they provided it")
        String name,

        @JsonProperty(required = true) // Tells the LLM this field is mandatory
        @JsonPropertyDescription("The email address of this user")
        String email,

        @JsonProperty(required = false)
        @JsonPropertyDescription("Any additional info about the conversation that's worth recording to give context")
        String note
) {
}
