package si.um.feri.dotaops.backend.notification.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.ResourceNotFoundException;
import si.um.feri.dotaops.backend.notification.repository.NotificationOutboxRepository;
import si.um.feri.dotaops.backend.notification.web.NotificationResponse;
import si.um.feri.dotaops.backend.notification.web.NotificationsReadResponse;

@Service
public class NotificationService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final CurrentUserProvider currentUserProvider;

    public NotificationService(
            NotificationOutboxRepository notificationOutboxRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.notificationOutboxRepository = notificationOutboxRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listCurrentUserNotifications(Integer limit) {
        UUID profileId = currentUserProvider.requireProfileId();
        return notificationOutboxRepository.findInAppByRecipientProfileId(profileId, safeLimit(limit))
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public NotificationResponse markRead(UUID notificationId) {
        UUID profileId = currentUserProvider.requireProfileId();
        return notificationOutboxRepository.markRead(notificationId, profileId)
                .map(NotificationResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));
    }

    @Transactional
    public NotificationsReadResponse markAllRead() {
        UUID profileId = currentUserProvider.requireProfileId();
        return new NotificationsReadResponse(notificationOutboxRepository.markAllRead(profileId));
    }

    private int safeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }

        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}
