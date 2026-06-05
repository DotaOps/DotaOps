package si.um.feri.dotaops.backend.analytics.web;

import java.util.UUID;

import si.um.feri.dotaops.backend.analytics.repository.AnalyticsLookupRepository;

public record PlayerComparisonCandidateResponse(
        UUID profileId,
        String displayName,
        String nickname,
        UUID teamId,
        String teamName
) {

    public static PlayerComparisonCandidateResponse from(
            AnalyticsLookupRepository.PlayerComparisonCandidate candidate
    ) {
        return new PlayerComparisonCandidateResponse(
                candidate.profileId(),
                candidate.displayName(),
                candidate.nickname(),
                candidate.teamId(),
                candidate.teamName());
    }
}
