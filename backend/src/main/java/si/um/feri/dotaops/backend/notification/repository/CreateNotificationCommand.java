package si.um.feri.dotaops.backend.notification.repository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import si.um.feri.dotaops.backend.notification.domain.NotificationChannel;
import si.um.feri.dotaops.backend.notification.domain.NotificationType;

public record CreateNotificationCommand(
        UUID recipientProfileId,
        NotificationType type,
        NotificationChannel channel,
        String title,
        String message,
        Map<String, Object> payload,
        OffsetDateTime nextAttemptAt
) {
}
