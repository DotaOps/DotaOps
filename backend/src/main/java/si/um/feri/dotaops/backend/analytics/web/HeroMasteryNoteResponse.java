package si.um.feri.dotaops.backend.analytics.web;

import java.math.BigDecimal;

public record HeroMasteryNoteResponse(
        HeroMasteryNoteCategory category,
        HeroMasteryNoteSeverity severity,
        String message,
        String metricName,
        BigDecimal currentValue,
        BigDecimal baselineValue
) {
}
