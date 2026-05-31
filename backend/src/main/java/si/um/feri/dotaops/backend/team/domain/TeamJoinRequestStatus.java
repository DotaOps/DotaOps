package si.um.feri.dotaops.backend.team.domain;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TeamJoinRequestStatus {
    PENDING("pending"),
    ACCEPTED("accepted"),
    DECLINED("declined"),
    CANCELLED("cancelled");

    private final String databaseValue;

    TeamJoinRequestStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    @JsonValue
    public String databaseValue() {
        return databaseValue;
    }

    @JsonCreator
    public static TeamJoinRequestStatus fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Team join request status is required.");
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (TeamJoinRequestStatus status : values()) {
            if (status.databaseValue.equals(normalized)) {
                return status;
            }
        }

        throw new IllegalArgumentException("Unknown team join request status: " + value);
    }
}
