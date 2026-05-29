package si.um.feri.dotaops.backend.notification.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import si.um.feri.dotaops.backend.notification.domain.NotificationChannel;
import si.um.feri.dotaops.backend.notification.domain.NotificationOutbox;
import si.um.feri.dotaops.backend.notification.domain.NotificationStatus;
import si.um.feri.dotaops.backend.notification.domain.NotificationType;
import si.um.feri.dotaops.backend.notification.repository.NotificationOutboxRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationOutboxProcessorTest {

    private static final UUID NOTIFICATION_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID SECOND_NOTIFICATION_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PROFILE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-29T12:00:00Z");

    private final NotificationOutboxRepository notificationOutboxRepository = mock(NotificationOutboxRepository.class);

    @Test
    void queuedInAppNotificationIsDelivered() {
        NotificationOutbox queued = notification(NOTIFICATION_ID, NotificationStatus.QUEUED, 0);
        when(notificationOutboxRepository.findDueQueued(any(OffsetDateTime.class), anyInt()))
                .thenReturn(List.of(queued));
        when(notificationOutboxRepository.markProcessing(NOTIFICATION_ID))
                .thenReturn(Optional.of(notification(NOTIFICATION_ID, NotificationStatus.PROCESSING, 0)));

        NotificationOutboxProcessor processor = new NotificationOutboxProcessor(
                notificationOutboxRepository,
                List.of(successfulProvider()));

        var response = processor.processQueued();

        assertThat(response.processedCount()).isEqualTo(1);
        assertThat(response.deliveredCount()).isEqualTo(1);
        assertThat(response.failedCount()).isZero();
        verify(notificationOutboxRepository).markDelivered(NOTIFICATION_ID);
    }

    @Test
    void failedDeliveryStoresErrorAndSchedulesRetry() {
        NotificationOutbox queued = notification(NOTIFICATION_ID, NotificationStatus.QUEUED, 1);
        when(notificationOutboxRepository.findDueQueued(any(OffsetDateTime.class), anyInt()))
                .thenReturn(List.of(queued));
        when(notificationOutboxRepository.markProcessing(NOTIFICATION_ID))
                .thenReturn(Optional.of(notification(NOTIFICATION_ID, NotificationStatus.PROCESSING, 1)));

        NotificationOutboxProcessor processor = new NotificationOutboxProcessor(
                notificationOutboxRepository,
                List.of(failingProvider()));

        var response = processor.processQueued();

        assertThat(response.processedCount()).isEqualTo(1);
        assertThat(response.deliveredCount()).isZero();
        assertThat(response.failedCount()).isEqualTo(1);
        verify(notificationOutboxRepository).markDeliveryFailure(
                eq(NOTIFICATION_ID),
                eq("delivery unavailable"),
                any(OffsetDateTime.class),
                eq(3));
    }

    @Test
    void failedDeliveryAtMaxAttemptIsDelegatedAsPermanentFailure() {
        NotificationOutbox queued = notification(NOTIFICATION_ID, NotificationStatus.QUEUED, 2);
        when(notificationOutboxRepository.findDueQueued(any(OffsetDateTime.class), anyInt()))
                .thenReturn(List.of(queued));
        when(notificationOutboxRepository.markProcessing(NOTIFICATION_ID))
                .thenReturn(Optional.of(notification(NOTIFICATION_ID, NotificationStatus.PROCESSING, 2)));
        when(notificationOutboxRepository.markDeliveryFailure(
                eq(NOTIFICATION_ID),
                eq("delivery unavailable"),
                any(OffsetDateTime.class),
                eq(3)))
                .thenReturn(Optional.of(notification(NOTIFICATION_ID, NotificationStatus.FAILED, 3)));

        NotificationOutboxProcessor processor = new NotificationOutboxProcessor(
                notificationOutboxRepository,
                List.of(failingProvider()));

        var response = processor.processQueued();

        assertThat(response.failedCount()).isEqualTo(1);
        verify(notificationOutboxRepository).markDeliveryFailure(
                eq(NOTIFICATION_ID),
                eq("delivery unavailable"),
                any(OffsetDateTime.class),
                eq(3));
    }

    @Test
    void failureForOneNotificationDoesNotStopBatch() {
        NotificationOutbox first = notification(NOTIFICATION_ID, NotificationStatus.QUEUED, 0);
        NotificationOutbox second = notification(SECOND_NOTIFICATION_ID, NotificationStatus.QUEUED, 0);
        when(notificationOutboxRepository.findDueQueued(any(OffsetDateTime.class), anyInt()))
                .thenReturn(List.of(first, second));
        when(notificationOutboxRepository.markProcessing(NOTIFICATION_ID))
                .thenReturn(Optional.of(notification(NOTIFICATION_ID, NotificationStatus.PROCESSING, 0)));
        when(notificationOutboxRepository.markProcessing(SECOND_NOTIFICATION_ID))
                .thenReturn(Optional.of(notification(SECOND_NOTIFICATION_ID, NotificationStatus.PROCESSING, 0)));

        NotificationOutboxProcessor processor = new NotificationOutboxProcessor(
                notificationOutboxRepository,
                List.of(providerFailingOnlyFirst()));

        var response = processor.processQueued();

        assertThat(response.processedCount()).isEqualTo(2);
        assertThat(response.deliveredCount()).isEqualTo(1);
        assertThat(response.failedCount()).isEqualTo(1);
        verify(notificationOutboxRepository).markDelivered(SECOND_NOTIFICATION_ID);
    }

    private static NotificationDeliveryProvider successfulProvider() {
        return new NotificationDeliveryProvider() {
            @Override
            public NotificationChannel channel() {
                return NotificationChannel.IN_APP;
            }

            @Override
            public void deliver(NotificationOutbox notification) {
            }
        };
    }

    private static NotificationDeliveryProvider failingProvider() {
        return new NotificationDeliveryProvider() {
            @Override
            public NotificationChannel channel() {
                return NotificationChannel.IN_APP;
            }

            @Override
            public void deliver(NotificationOutbox notification) {
                throw new IllegalStateException("delivery unavailable");
            }
        };
    }

    private static NotificationDeliveryProvider providerFailingOnlyFirst() {
        return new NotificationDeliveryProvider() {
            @Override
            public NotificationChannel channel() {
                return NotificationChannel.IN_APP;
            }

            @Override
            public void deliver(NotificationOutbox notification) {
                if (NOTIFICATION_ID.equals(notification.id())) {
                    throw new IllegalStateException("delivery unavailable");
                }
            }
        };
    }

    private static NotificationOutbox notification(UUID id, NotificationStatus status, int attemptCount) {
        return new NotificationOutbox(
                id,
                PROFILE_ID,
                NotificationType.MATCH_SCHEDULED,
                NotificationChannel.IN_APP,
                "Tekma razporejena",
                "Tvoja ekipa ima razporejeno tekmo.",
                Map.of("matchId", id.toString()),
                status,
                attemptCount,
                null,
                NOW,
                null,
                null,
                NOW.minusMinutes(5),
                NOW);
    }
}
