package si.um.feri.dotaops.backend.notification.service;

import org.springframework.stereotype.Component;

import si.um.feri.dotaops.backend.notification.domain.NotificationChannel;
import si.um.feri.dotaops.backend.notification.domain.NotificationOutbox;

@Component
public class EmailNotificationDeliveryProvider implements NotificationDeliveryProvider {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void deliver(NotificationOutbox notification) {
        // Placeholder for a future email transport integration.
    }
}
