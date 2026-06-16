package si.um.feri.dotaops.backend.analytics.web;

import java.math.BigDecimal;

public record HeroMasteryTrendMetricResponse(
        String metric,
        BigDecimal recentValue,
        BigDecimal previousValue,
        BigDecimal delta,
        HeroMasteryComparisonDirection direction,
        String interpretation
) {
}
