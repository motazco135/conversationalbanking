package io.github.motazco135.conversationalbanking.infrastructure.push.dto;

public record PushoverRequest(
        String token,   // Your Pushover Application API Token
        String user,    // Your Pushover User Key
        String message, // The notification message text
        String title    // Optional: Notification title
) {}