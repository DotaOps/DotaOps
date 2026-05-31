package si.um.feri.dotaops.backend.tournament.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import si.um.feri.dotaops.backend.tournament.domain.TournamentRegistrationMember;

public record TournamentRegistrationMemberResponse(
        UUID id,
        UUID profileId,
        String nickname,
        String displayName,
        String avatarUrl,
        UUID teamMemberId,
        UUID manualPlayerId,
        String note,
        String role,
        boolean manual,
        boolean starter,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static TournamentRegistrationMemberResponse from(TournamentRegistrationMember member) {
        return new TournamentRegistrationMemberResponse(
                member.id(),
                member.profileId(),
                member.nickname(),
                member.displayName(),
                member.avatarUrl(),
                member.teamMemberId(),
                member.manualPlayerId(),
                member.note(),
                member.memberRole() == null ? null : member.memberRole().databaseValue(),
                member.manualPlayerId() != null,
                member.starter(),
                member.createdAt(),
                member.updatedAt());
    }
}
