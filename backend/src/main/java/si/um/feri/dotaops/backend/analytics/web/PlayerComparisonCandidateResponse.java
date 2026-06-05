package si.um.feri.dotaops.backend.analytics.web;

import java.util.UUID;

import si.um.feri.dotaops.backend.analytics.repository.AnalyticsLookupRepository;

public record PlayerComparisonCandidateResponse(
        UUID profileId,
        String displayName,
        String nickname,
        UUID teamId,
        String teamName,
        String avatarUrl,
        Long opendotaAccountId,
        int analyticsGamesCount,
        boolean hasAnalyticsData,
        String label
) {

    /*
     * Autocomplete candidates intentionally expose only non-sensitive profile fields
     * already used in profile/roster views; auth ids, emails and account secrets stay out.
     */
    public static PlayerComparisonCandidateResponse from(
            AnalyticsLookupRepository.PlayerComparisonCandidate candidate
    ) {
        return new PlayerComparisonCandidateResponse(
                candidate.profileId(),
                candidate.displayName(),
                candidate.nickname(),
                candidate.teamId(),
                candidate.teamName(),
                candidate.avatarUrl(),
                candidate.opendotaAccountId(),
                candidate.analyticsGamesCount(),
                candidate.hasAnalyticsData(),
                candidate.label());
    }
}
