package si.um.feri.dotaops.backend.team.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import si.um.feri.dotaops.backend.analytics.web.AnalyticsMatchHistoryResponse;
import si.um.feri.dotaops.backend.analytics.web.HeroMetricsResponse;
import si.um.feri.dotaops.backend.team.domain.TeamMemberRole;

public record TeamRosterProfileResponse(
        UUID profileId,
        String nickname,
        String displayName,
        String avatarUrl,
        TeamMemberRole role,
        boolean teamOwner,
        OffsetDateTime joinedAt,
        TeamRosterPlayerStatsResponse stats,
        List<HeroMetricsResponse> mostPlayedHeroes,
        List<AnalyticsMatchHistoryResponse> recentMatches
) {
}
