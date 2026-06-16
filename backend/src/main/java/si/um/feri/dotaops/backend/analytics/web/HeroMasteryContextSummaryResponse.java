package si.um.feri.dotaops.backend.analytics.web;

import java.math.BigDecimal;

public record HeroMasteryContextSummaryResponse(
        BigDecimal averageContextWeight,
        int roughGameCount,
        int stompLossCount,
        int lowConfidenceCount,
        int normalGameCount,
        String note
) {
}
