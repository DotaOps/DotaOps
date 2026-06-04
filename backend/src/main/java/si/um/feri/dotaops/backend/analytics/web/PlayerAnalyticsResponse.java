package si.um.feri.dotaops.backend.analytics.web;

import java.util.List;

public record PlayerAnalyticsResponse(
        List<PlayerMetricsResponse> metrics,
        List<HeroMetricsResponse> heroPerformance,
        List<AnalyticsMatchHistoryResponse> matchHistory
) {
}
