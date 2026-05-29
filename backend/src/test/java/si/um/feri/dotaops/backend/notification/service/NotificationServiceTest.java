package si.um.feri.dotaops.backend.notification.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.ResourceNotFoundException;
import si.um.feri.dotaops.backend.notification.domain.NotificationChannel;
import si.um.feri.dotaops.backend.notification.domain.NotificationOutbox;
import si.um.feri.dotaops.backend.notification.domain.NotificationStatus;
import si.um.feri.dotaops.backend.notification.domain.NotificationType;
import si.um.feri.dotaops.backend.notification.repository.NotificationOutboxRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private static final UUID PROFILE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER_PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID NOTIFICATION_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-29T12:00:00Z");

    private final NotificationOutboxRepository notificationOutboxRepository = mock(NotificationOutboxRepository.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final NotificationService service = new NotificationService(notificationOutboxRepository, currentUserProvider);

    @Test
    void currentUserSeesOnlyOwnNotifications() {
        when(currentUserProvider.requireProfileId()).thenReturn(PROFILE_ID);
        when(notificationOutboxRepository.findInAppByRecipientProfileId(PROFILE_ID, 50))
                .thenReturn(List.of(notification(PROFILE_ID, null)));

        var response = service.listCurrentUserNotifications(null);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().id()).isEqualTo(NOTIFICATION_ID);
        verify(notificationOutboxRepository).findInAppByRecipientProfileId(PROFILE_ID, 50);
    }

    @Test
    void userCanMarkOwnNotificationAsRead() {
        when(currentUserProvider.requireProfileId()).thenReturn(PROFILE_ID);
        when(notificationOutboxRepository.markRead(NOTIFICATION_ID, PROFILE_ID))
                .thenReturn(Optional.of(notification(PROFILE_ID, NOW)));

        var response = service.markRead(NOTIFICATION_ID);

        assertThat(response.readAt()).isEqualTo(NOW);
    }

    @Test
    void userCannotMarkForeignNotificationAsRead() {
        when(currentUserProvider.requireProfileId()).thenReturn(PROFILE_ID);
        when(notificationOutboxRepository.markRead(NOTIFICATION_ID, PROFILE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(NOTIFICATION_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(NOTIFICATION_ID.toString());
    }

    @Test
    void readAllMarksOnlyCurrentUsersNotifications() {
        when(currentUserProvider.requireProfileId()).thenReturn(PROFILE_ID);
        when(notificationOutboxRepository.markAllRead(PROFILE_ID)).thenReturn(2);

        var response = service.markAllRead();

        assertThat(response.updatedCount()).isEqualTo(2);
        verify(notificationOutboxRepository).markAllRead(PROFILE_ID);
    }

    private static NotificationOutbox notification(UUID recipientProfileId, OffsetDateTime readAt) {
        return new NotificationOutbox(
                NOTIFICATION_ID,
                recipientProfileId,
                NotificationType.TEAM_APPLICATION_APPROVED,
                NotificationChannel.IN_APP,
                "Ekipa odobrena",
                "Tvoja ekipa je bila odobrena.",
                Map.of("teamId", OTHER_PROFILE_ID.toString()),
                NotificationStatus.DELIVERED,
                0,
                null,
                NOW,
                NOW,
                readAt,
                NOW.minusMinutes(5),
                NOW);
    }
}
