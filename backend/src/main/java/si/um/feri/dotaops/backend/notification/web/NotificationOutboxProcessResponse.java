package si.um.feri.dotaops.backend.notification.web;

public record NotificationOutboxProcessResponse(
        int processedCount,
        int deliveredCount,
        int failedCount
) {
}
