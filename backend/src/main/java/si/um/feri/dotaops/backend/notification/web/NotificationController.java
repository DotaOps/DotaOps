package si.um.feri.dotaops.backend.notification.web;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import si.um.feri.dotaops.backend.common.api.ApiResponse;
import si.um.feri.dotaops.backend.notification.service.NotificationService;

@RestController
@RequestMapping("/api/me/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    ApiResponse<List<NotificationResponse>> listMyNotifications(
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        return ApiResponse.of(notificationService.listCurrentUserNotifications(limit));
    }

    @PostMapping("/{notificationId}/read")
    ApiResponse<NotificationResponse> markRead(@PathVariable UUID notificationId) {
        return ApiResponse.of(notificationService.markRead(notificationId));
    }

    @PostMapping("/read-all")
    ApiResponse<NotificationsReadResponse> markAllRead() {
        return ApiResponse.of(notificationService.markAllRead());
    }
}
