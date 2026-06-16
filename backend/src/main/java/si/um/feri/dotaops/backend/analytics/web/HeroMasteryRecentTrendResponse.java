package si.um.feri.dotaops.backend.analytics.web;

import java.util.List;

public record HeroMasteryRecentTrendResponse(
        int sampleSize,
        int recentWindowSize,
        int previousWindowSize,
        List<HeroMasteryTrendMetricResponse> metrics,
        String interpretation
) {
}
