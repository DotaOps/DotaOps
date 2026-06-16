package si.um.feri.dotaops.backend.analytics.web;

import java.math.BigDecimal;

public record HeroMasteryMetricComparisonResponse(
        String metric,
        BigDecimal heroValue,
        BigDecimal overallValue,
        BigDecimal delta,
        HeroMasteryComparisonDirection direction,
        String interpretation
) {
}
