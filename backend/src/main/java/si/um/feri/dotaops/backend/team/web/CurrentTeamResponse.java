package si.um.feri.dotaops.backend.team.web;

import java.util.List;

public record CurrentTeamResponse(
        TeamResponse team,
        List<TeamMemberResponse> members,
        List<TeamManualPlayerResponse> manualPlayers,
        boolean captain,
        boolean isTeamOwner,
        String currentUserTeamRole,
        boolean canCreateTeam,
        boolean canManageTeam,
        boolean canManageRoster,
        boolean canInvitePlayers,
        boolean canTransferOwnership,
        boolean canViewAnalytics,
        int participantsCount,
        int capacity,
        int slotsFilled,
        int slotsRemaining,
        boolean isFull,
        String teamResolution
) {

    public CurrentTeamResponse(
            TeamResponse team,
            List<TeamMemberResponse> members,
            boolean captain,
            boolean canManageRoster,
            String teamResolution
    ) {
        this(
                team,
                members,
                List.of(),
                captain,
                captain,
                captain ? "owner" : "member",
                false,
                canManageRoster,
                canManageRoster,
                canManageRoster,
                captain,
                team != null,
                members == null ? 0 : members.size(),
                0,
                members == null ? 0 : members.size(),
                0,
                false,
                teamResolution);
    }

    public static CurrentTeamResponse none() {
        return none(false);
    }

    public static CurrentTeamResponse none(boolean canCreateTeam) {
        return new CurrentTeamResponse(
                null,
                List.of(),
                List.of(),
                false,
                false,
                null,
                canCreateTeam,
                false,
                false,
                false,
                false,
                false,
                0,
                0,
                0,
                0,
                false,
                "No active team found for the current profile.");
    }
}
