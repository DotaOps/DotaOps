package si.um.feri.dotaops.backend.dashboard.web;

public record OrganizerDashboardResponse(
        long tournaments,
        long pendingRegistrations,
        long activePublishedTournaments,
        long processedMatchGames,
        long importJobs
) {
}
