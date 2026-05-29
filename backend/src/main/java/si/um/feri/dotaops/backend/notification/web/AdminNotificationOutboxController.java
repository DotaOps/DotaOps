package si.um.feri.dotaops.backend.notification.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import si.um.feri.dotaops.backend.common.api.ApiResponse;
import si.um.feri.dotaops.backend.notification.service.NotificationOutboxProcessor;

@RestController
@RequestMapping("/api/admin/notifications/outbox")
public class AdminNotificationOutboxController {

    private final NotificationOutboxProcessor notificationOutboxProcessor;

    public AdminNotificationOutboxController(NotificationOutboxProcessor notificationOutboxProcessor) {
        this.notificationOutboxProcessor = notificationOutboxProcessor;
    }

    @PostMapping("/process")
    ApiResponse<NotificationOutboxProcessResponse> processQueuedNotifications() {
        return ApiResponse.of(notificationOutboxProcessor.processQueued());
    }
}
