package si.um.feri.dotaops.backend.analytics.web;

import java.util.UUID;

public record PlayerComparisonWarningResponse(
        String code,
        String severity,
        String message,
        UUID profileId,
        UUID heroId,
        String metricName,
        int sampleSize,
        int recommendedMinimum
) {
}
