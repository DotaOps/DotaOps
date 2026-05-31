package si.um.feri.dotaops.backend.team.repository;

public record UpdateTeamManualPlayerCommand(
        boolean displayNamePresent,
        String displayName,
        boolean nicknamePresent,
        String nickname,
        boolean notePresent,
        String note
) {
}
