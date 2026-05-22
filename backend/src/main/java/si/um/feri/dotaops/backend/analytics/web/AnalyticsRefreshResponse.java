package si.um.feri.dotaops.backend.analytics.web;

import java.time.OffsetDateTime;

public record AnalyticsRefreshResponse(
        String status,
        String reason,
        OffsetDateTime requestedAt,
        OffsetDateTime completedAt,
        Long durationMs,
        String message
) {
}
