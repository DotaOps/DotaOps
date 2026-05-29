package si.um.feri.dotaops.backend.tournament.dto;

import java.util.UUID;

import si.um.feri.dotaops.backend.tournament.domain.PublicManualPlayer;

public record PublicManualPlayerResponse(
        UUID id,
        String displayName,
        String nickname,
        String note
) {

    public static PublicManualPlayerResponse from(PublicManualPlayer manualPlayer) {
        return new PublicManualPlayerResponse(
                manualPlayer.id(),
                manualPlayer.displayName(),
                manualPlayer.nickname(),
                manualPlayer.note());
    }
}
