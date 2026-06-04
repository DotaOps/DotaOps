package si.um.feri.dotaops.backend.analytics.web;

import java.util.UUID;

import si.um.feri.dotaops.backend.analytics.repository.AnalyticsLookupRepository;

public record AnalyticsPlayerLookupResponse(
        UUID profileId,
        String displayName,
        String nickname,
        UUID teamId,
        String teamName
) {

    public static AnalyticsPlayerLookupResponse from(AnalyticsLookupRepository.PlayerLookup lookup) {
        return new AnalyticsPlayerLookupResponse(
                lookup.profileId(),
                lookup.displayName(),
                lookup.nickname(),
                lookup.teamId(),
                lookup.teamName());
    }
}
