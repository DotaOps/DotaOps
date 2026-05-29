package si.um.feri.dotaops.backend.team.web;

import java.time.OffsetDateTime;
import java.util.UUID;

import si.um.feri.dotaops.backend.team.domain.TeamManualPlayer;

public record TeamManualPlayerResponse(
        UUID id,
        UUID teamId,
        String displayName,
        String nickname,
        String note,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static TeamManualPlayerResponse from(TeamManualPlayer manualPlayer) {
        return new TeamManualPlayerResponse(
                manualPlayer.id(),
                manualPlayer.teamId(),
                manualPlayer.displayName(),
                manualPlayer.nickname(),
                manualPlayer.note(),
                manualPlayer.createdAt(),
                manualPlayer.updatedAt());
    }
}
