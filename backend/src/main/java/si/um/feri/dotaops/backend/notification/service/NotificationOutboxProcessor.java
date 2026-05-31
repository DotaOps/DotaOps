package si.um.feri.dotaops.backend.notification.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import si.um.feri.dotaops.backend.notification.domain.NotificationChannel;
import si.um.feri.dotaops.backend.notification.domain.NotificationOutbox;
import si.um.feri.dotaops.backend.notification.repository.NotificationOutboxRepository;
import si.um.feri.dotaops.backend.notification.web.NotificationOutboxProcessResponse;

@Service
public class NotificationOutboxProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationOutboxProcessor.class);
    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_BACKOFF = Duration.ofMinutes(15);

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final Map<NotificationChannel, NotificationDeliveryProvider> providers;

    public NotificationOutboxProcessor(
            NotificationOutboxRepository notificationOutboxRepository,
            List<NotificationDeliveryProvider> providers
    ) {
        this.notificationOutboxRepository = notificationOutboxRepository;
        this.providers = new EnumMap<>(NotificationChannel.class);
        for (NotificationDeliveryProvider provider : providers) {
            this.providers.put(provider.channel(), provider);
        }
    }

    public NotificationOutboxProcessResponse processQueued() {
        return processQueued(DEFAULT_BATCH_SIZE);
    }

    public NotificationOutboxProcessResponse processQueued(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, DEFAULT_BATCH_SIZE));
        List<NotificationOutbox> queued = notificationOutboxRepository.findDueQueued(now(), safeLimit);

        int processed = 0;
        int delivered = 0;
        int failed = 0;

        for (NotificationOutbox notification : queued) {
            Optional<NotificationOutbox> processing = notificationOutboxRepository.markProcessing(notification.id());
            if (processing.isEmpty()) {
                continue;
            }

            processed++;
            try {
                providerFor(processing.get()).deliver(processing.get());
                notificationOutboxRepository.markDelivered(processing.get().id());
                delivered++;
            } catch (RuntimeException exception) {
                LOGGER.warn("Notification delivery failed for outbox id {}.", processing.get().id(), exception);
                markDeliveryFailureSafely(processing.get(), exception);
                failed++;
            }
        }

        return new NotificationOutboxProcessResponse(processed, delivered, failed);
    }

    private void markDeliveryFailureSafely(NotificationOutbox notification, RuntimeException exception) {
        try {
            notificationOutboxRepository.markDeliveryFailure(
                    notification.id(),
                    safeError(exception),
                    now().plus(RETRY_BACKOFF),
                    MAX_ATTEMPTS);
        } catch (RuntimeException repositoryException) {
            LOGGER.warn(
                    "Notification outbox failure state update failed for outbox id {}.",
                    notification.id(),
                    repositoryException);
        }
    }

    private NotificationDeliveryProvider providerFor(NotificationOutbox notification) {
        NotificationDeliveryProvider provider = providers.get(notification.channel());
        if (provider == null) {
            throw new IllegalStateException("No notification delivery provider registered for " + notification.channel());
        }

        return provider;
    }

    private String safeError(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return message;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
