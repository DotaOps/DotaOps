package si.um.feri.dotaops.backend.analytics.web;

public record OrganizerAnalyticsResponse(
        long tournaments,
        long pendingRegistrations,
        long approvedRegistrations,
        long activePublishedTournaments,
        long processedMatchGames,
        long importJobs
) {
}
