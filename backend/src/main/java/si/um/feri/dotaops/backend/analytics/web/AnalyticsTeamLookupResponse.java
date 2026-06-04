package si.um.feri.dotaops.backend.analytics.web;

import java.util.UUID;

import si.um.feri.dotaops.backend.analytics.repository.AnalyticsLookupRepository;

public record AnalyticsTeamLookupResponse(
        UUID teamId,
        String name,
        String tag
) {

    public static AnalyticsTeamLookupResponse from(AnalyticsLookupRepository.TeamLookup lookup) {
        return new AnalyticsTeamLookupResponse(
                lookup.teamId(),
                lookup.name(),
                lookup.tag());
    }
}
