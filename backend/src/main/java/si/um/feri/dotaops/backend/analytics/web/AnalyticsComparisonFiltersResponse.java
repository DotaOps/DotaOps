package si.um.feri.dotaops.backend.analytics.web;

import java.time.OffsetDateTime;
import java.util.UUID;

import si.um.feri.dotaops.backend.analytics.domain.AnalyticsFilters;

public record AnalyticsComparisonFiltersResponse(
        UUID tournamentId,
        UUID teamId,
        UUID profileId,
        UUID heroId,
        OffsetDateTime from,
        OffsetDateTime to,
        int limit,
        String accessScope
) {

    public static AnalyticsComparisonFiltersResponse from(AnalyticsFilters filters, String accessScope) {
        return new AnalyticsComparisonFiltersResponse(
                filters.tournamentId(),
                filters.teamId(),
                filters.profileId(),
                filters.heroId(),
                filters.from(),
                filters.to(),
                filters.limit(),
                accessScope);
    }
}
