package si.um.feri.dotaops.backend.analytics.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import si.um.feri.dotaops.backend.common.error.BadRequestException;

public record AnalyticsFilters(
        UUID tournamentId,
        UUID teamId,
        UUID profileId,
        UUID heroId,
        OffsetDateTime from,
        OffsetDateTime to,
        int limit
) {

    public static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    public AnalyticsFilters {
        limit = Math.min(Math.max(limit <= 0 ? DEFAULT_LIMIT : limit, 1), MAX_LIMIT);
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("Analytics time range is invalid.");
        }
    }

    public AnalyticsFilters(
            UUID tournamentId,
            UUID teamId,
            UUID profileId,
            UUID heroId,
            int limit
    ) {
        this(tournamentId, teamId, profileId, heroId, null, null, limit);
    }

    public AnalyticsFilters withTournamentId(UUID tournamentId) {
        return new AnalyticsFilters(tournamentId, teamId, profileId, heroId, from, to, limit);
    }

    public AnalyticsFilters withTeamId(UUID teamId) {
        return new AnalyticsFilters(tournamentId, teamId, profileId, heroId, from, to, limit);
    }

    public AnalyticsFilters withProfileId(UUID profileId) {
        return new AnalyticsFilters(tournamentId, teamId, profileId, heroId, from, to, limit);
    }

    public AnalyticsFilters withHeroId(UUID heroId) {
        return new AnalyticsFilters(tournamentId, teamId, profileId, heroId, from, to, limit);
    }

    public AnalyticsFilters withLimit(int limit) {
        return new AnalyticsFilters(tournamentId, teamId, profileId, heroId, from, to, limit);
    }
}
