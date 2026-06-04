package si.um.feri.dotaops.backend.analytics.web;

import java.util.UUID;

import si.um.feri.dotaops.backend.analytics.repository.AnalyticsLookupRepository;

public record AnalyticsTournamentLookupResponse(
        UUID tournamentId,
        String title,
        String status
) {

    public static AnalyticsTournamentLookupResponse from(AnalyticsLookupRepository.TournamentLookup lookup) {
        return new AnalyticsTournamentLookupResponse(
                lookup.tournamentId(),
                lookup.title(),
                lookup.status());
    }
}
