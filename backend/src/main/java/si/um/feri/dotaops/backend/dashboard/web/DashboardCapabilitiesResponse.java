package si.um.feri.dotaops.backend.dashboard.web;

public record DashboardCapabilitiesResponse(
        boolean canCreateTeam,
        boolean isTeamOwner,
        String currentUserTeamRole,
        boolean canManageTeam,
        boolean canManageRoster,
        boolean canInvitePlayers,
        boolean canViewAnalytics,
        boolean canManageTournament,
        boolean canViewOrganizerDashboard
) {
}
