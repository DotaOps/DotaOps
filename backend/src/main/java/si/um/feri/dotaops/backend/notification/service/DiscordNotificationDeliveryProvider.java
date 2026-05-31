package si.um.feri.dotaops.backend.notification.service;

import org.springframework.stereotype.Component;

import si.um.feri.dotaops.backend.notification.domain.NotificationChannel;
import si.um.feri.dotaops.backend.notification.domain.NotificationOutbox;

@Component
public class DiscordNotificationDeliveryProvider implements NotificationDeliveryProvider {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.DISCORD;
    }

    @Override
    public void deliver(NotificationOutbox notification) {
        // Placeholder for a future Discord transport integration.
    }
}
