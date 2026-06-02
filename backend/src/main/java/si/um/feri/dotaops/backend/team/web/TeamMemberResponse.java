package si.um.feri.dotaops.backend.team.web;

import java.time.OffsetDateTime;
import java.util.UUID;

import si.um.feri.dotaops.backend.team.domain.TeamMember;
import si.um.feri.dotaops.backend.team.domain.TeamMemberRole;

public record TeamMemberResponse(
        UUID id,
        UUID teamId,
        UUID profileId,
        String nickname,
        String displayName,
        String avatarUrl,
        TeamMemberRole role,
        boolean teamOwner,
        boolean active,
        OffsetDateTime joinedAt,
        OffsetDateTime leftAt,
        OffsetDateTime updatedAt
) {

    public static TeamMemberResponse from(TeamMember member) {
        return from(member, null);
    }

    public static TeamMemberResponse from(TeamMember member, UUID captainProfileId) {
        return new TeamMemberResponse(
                member.id(),
                member.teamId(),
                member.profileId(),
                member.nickname(),
                member.displayName(),
                member.avatarUrl(),
                member.role(),
                member.profileId().equals(captainProfileId),
                member.active(),
                member.joinedAt(),
                member.leftAt(),
                member.updatedAt());
    }
}
