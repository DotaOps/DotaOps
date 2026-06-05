package si.um.feri.dotaops.backend.analytics.web;

import java.math.BigDecimal;

public record PlayerInsightResponse(
        String title,
        String description,
        PlayerInsightCategory category,
        String metricName,
        BigDecimal currentValue,
        BigDecimal comparisonValue,
        int sampleSize,
        String evidence
) {
}
