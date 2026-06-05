package si.um.feri.dotaops.backend.analytics.web;

public record PlayerComparisonHeadlineResponse(
        PlayerComparisonMetricResponse profileA,
        PlayerComparisonMetricResponse profileB,
        PlayerComparisonMetricDeltaResponse delta
) {
}
