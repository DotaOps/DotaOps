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
        String evidence,
        PlayerInsightContextResponse contextWeight
) {

    public PlayerInsightResponse(
            String title,
            String description,
            PlayerInsightCategory category,
            String metricName,
            BigDecimal currentValue,
            BigDecimal comparisonValue,
            int sampleSize,
            String evidence
    ) {
        this(title, description, category, metricName, currentValue, comparisonValue, sampleSize, evidence, null);
    }
}
