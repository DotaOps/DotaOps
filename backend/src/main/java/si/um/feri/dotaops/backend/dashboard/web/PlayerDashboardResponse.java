package si.um.feri.dotaops.backend.dashboard.web;

import si.um.feri.dotaops.backend.team.web.CurrentTeamResponse;

public record PlayerDashboardResponse(
        CurrentTeamResponse currentTeam,
        long pendingInvitations,
        long tournamentRegistrations
) {
}
