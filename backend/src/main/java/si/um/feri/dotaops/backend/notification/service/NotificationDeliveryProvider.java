package si.um.feri.dotaops.backend.notification.service;

import si.um.feri.dotaops.backend.notification.domain.NotificationChannel;
import si.um.feri.dotaops.backend.notification.domain.NotificationOutbox;

public interface NotificationDeliveryProvider {

    NotificationChannel channel();

    void deliver(NotificationOutbox notification);
}
