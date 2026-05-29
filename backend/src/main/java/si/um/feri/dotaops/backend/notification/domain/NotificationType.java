package si.um.feri.dotaops.backend.notification.domain;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum NotificationType {
    SYSTEM("system"),
    TEAM_APPLICATION_SUBMITTED("team_application_submitted"),
    TEAM_APPLICATION_APPROVED("team_application_approved"),
    TEAM_APPLICATION_REJECTED("team_application_rejected"),
    MATCH_SCHEDULED("match_scheduled");

    private final String databaseValue;

    NotificationType(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    @JsonValue
    public String databaseValue() {
        return databaseValue;
    }

    @JsonCreator
    public static NotificationType fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Notification type is required.");
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (NotificationType type : values()) {
            if (type.databaseValue.equals(normalized)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unknown notification type: " + value);
    }
}
