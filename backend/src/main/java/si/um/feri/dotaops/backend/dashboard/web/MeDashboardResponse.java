package si.um.feri.dotaops.backend.dashboard.web;

public record MeDashboardResponse(
        String role,
        DashboardCapabilitiesResponse capabilities,
        PlayerDashboardResponse player,
        OrganizerDashboardResponse organizer,
        AdminDashboardResponse admin
) {
}
