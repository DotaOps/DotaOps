package si.um.feri.dotaops.backend.notification.domain;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum NotificationChannel {
    IN_APP("in_app"),
    EMAIL("email"),
    DISCORD("discord");

    private final String databaseValue;

    NotificationChannel(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    @JsonValue
    public String databaseValue() {
        return databaseValue;
    }

    @JsonCreator
    public static NotificationChannel fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Notification channel is required.");
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (NotificationChannel channel : values()) {
            if (channel.databaseValue.equals(normalized)) {
                return channel;
            }
        }

        throw new IllegalArgumentException("Unknown notification channel: " + value);
    }
}
