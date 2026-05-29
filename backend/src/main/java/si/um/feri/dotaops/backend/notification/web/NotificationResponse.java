package si.um.feri.dotaops.backend.notification.web;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import si.um.feri.dotaops.backend.notification.domain.NotificationChannel;
import si.um.feri.dotaops.backend.notification.domain.NotificationOutbox;
import si.um.feri.dotaops.backend.notification.domain.NotificationStatus;
import si.um.feri.dotaops.backend.notification.domain.NotificationType;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        NotificationChannel channel,
        String title,
        String message,
        Map<String, Object> payload,
        NotificationStatus status,
        OffsetDateTime readAt,
        OffsetDateTime createdAt
) {

    public static NotificationResponse from(NotificationOutbox notification) {
        return new NotificationResponse(
                notification.id(),
                notification.type(),
                notification.channel(),
                notification.title(),
                notification.message(),
                notification.payload(),
                notification.status(),
                notification.readAt(),
                notification.createdAt());
    }
}
