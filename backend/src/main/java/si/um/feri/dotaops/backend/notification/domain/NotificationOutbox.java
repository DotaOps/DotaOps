package si.um.feri.dotaops.backend.notification.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record NotificationOutbox(
        UUID id,
        UUID recipientProfileId,
        NotificationType type,
        NotificationChannel channel,
        String title,
        String message,
        Map<String, Object> payload,
        NotificationStatus status,
        int attemptCount,
        String lastError,
        OffsetDateTime nextAttemptAt,
        OffsetDateTime processedAt,
        OffsetDateTime readAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public NotificationOutbox {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
