package si.um.feri.dotaops.backend.dashboard.web;

public record AdminDashboardResponse(
        long profiles,
        long tournaments,
        long pendingRegistrations,
        long importJobs
) {
}
