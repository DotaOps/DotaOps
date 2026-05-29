package si.um.feri.dotaops.backend.tournament.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import si.um.feri.dotaops.backend.team.domain.TeamMemberRole;

public record TournamentRegistrationMember(
        UUID id,
        UUID registrationId,
        UUID profileId,
        String nickname,
        String displayName,
        String avatarUrl,
        UUID teamMemberId,
        UUID manualPlayerId,
        String note,
        TeamMemberRole memberRole,
        boolean starter,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public TournamentRegistrationMember(
            UUID id,
            UUID registrationId,
            UUID profileId,
            String nickname,
            String displayName,
            String avatarUrl,
            UUID teamMemberId,
            TeamMemberRole memberRole,
            boolean starter,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this(
                id,
                registrationId,
                profileId,
                nickname,
                displayName,
                avatarUrl,
                teamMemberId,
                null,
                null,
                memberRole,
                starter,
                createdAt,
                updatedAt);
    }
}
