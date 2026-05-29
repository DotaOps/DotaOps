package si.um.feri.dotaops.backend.notification.service;

import org.springframework.stereotype.Component;

import si.um.feri.dotaops.backend.notification.domain.NotificationChannel;
import si.um.feri.dotaops.backend.notification.domain.NotificationOutbox;

@Component
public class InAppNotificationDeliveryProvider implements NotificationDeliveryProvider {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public void deliver(NotificationOutbox notification) {
        // The row itself is the in-app delivery artifact.
    }
}
