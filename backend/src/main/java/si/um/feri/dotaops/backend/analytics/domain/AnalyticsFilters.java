package si.um.feri.dotaops.backend.analytics.domain;

import java.util.UUID;

public record AnalyticsFilters(
        UUID tournamentId,
        UUID teamId,
        UUID profileId,
        UUID heroId,
        int limit
) {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    public AnalyticsFilters {
        limit = Math.min(Math.max(limit <= 0 ? DEFAULT_LIMIT : limit, 1), MAX_LIMIT);
    }

    public AnalyticsFilters withTournamentId(UUID tournamentId) {
        return new AnalyticsFilters(tournamentId, teamId, profileId, heroId, limit);
    }
}
