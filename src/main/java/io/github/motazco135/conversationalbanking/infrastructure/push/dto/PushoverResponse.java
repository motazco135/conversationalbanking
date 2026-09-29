package io.github.motazco135.conversationalbanking.infrastructure.push.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PushoverResponse(
        int status,
        String request,
        List<String> errors
) {}

