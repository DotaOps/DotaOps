package si.um.feri.dotaops.backend.audit.domain;

import java.util.Locale;

import si.um.feri.dotaops.backend.common.error.BadRequestException;

public enum AdminAuditAction {
    INSERT("insert", "inserted"),
    UPDATE("update", "updated"),
    DELETE("delete", "deleted");

    private final String databaseValue;
    private final String summaryVerb;

    AdminAuditAction(String databaseValue, String summaryVerb) {
        this.databaseValue = databaseValue;
        this.summaryVerb = summaryVerb;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public String summaryVerb() {
        return summaryVerb;
    }

    public static AdminAuditAction fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Audit action is required.");
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);

        for (AdminAuditAction action : values()) {
            if (action.databaseValue.equals(normalized)) {
                return action;
            }
        }

        throw new BadRequestException("Unsupported audit action.");
    }
}
