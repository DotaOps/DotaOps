package si.um.feri.dotaops.backend.notification.domain;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum NotificationStatus {
    QUEUED("queued"),
    PROCESSING("processing"),
    DELIVERED("delivered"),
    FAILED("failed");

    private final String databaseValue;

    NotificationStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    @JsonValue
    public String databaseValue() {
        return databaseValue;
    }

    @JsonCreator
    public static NotificationStatus fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Notification status is required.");
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("sent".equals(normalized)) {
            return DELIVERED;
        }
        if ("cancelled".equals(normalized)) {
            return FAILED;
        }

        for (NotificationStatus status : values()) {
            if (status.databaseValue.equals(normalized)) {
                return status;
            }
        }

        throw new IllegalArgumentException("Unknown notification status: " + value);
    }
}
