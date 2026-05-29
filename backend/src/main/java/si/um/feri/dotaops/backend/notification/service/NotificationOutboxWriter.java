package si.um.feri.dotaops.backend.notification.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import si.um.feri.dotaops.backend.notification.domain.NotificationOutbox;
import si.um.feri.dotaops.backend.notification.repository.CreateNotificationCommand;
import si.um.feri.dotaops.backend.notification.repository.NotificationOutboxRepository;

@Service
public class NotificationOutboxWriter {

    private final NotificationOutboxRepository notificationOutboxRepository;

    public NotificationOutboxWriter(NotificationOutboxRepository notificationOutboxRepository) {
        this.notificationOutboxRepository = notificationOutboxRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationOutbox enqueue(CreateNotificationCommand command) {
        return notificationOutboxRepository.create(command);
    }
}
