package si.um.feri.dotaops.backend.team.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import si.um.feri.dotaops.backend.team.domain.Team;
import si.um.feri.dotaops.backend.team.repository.TeamManualPlayerRepository;
import si.um.feri.dotaops.backend.team.repository.TeamMemberRepository;
import si.um.feri.dotaops.backend.team.repository.TeamRosterLimitRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamRosterCapacityServiceTest {

    private static final UUID TEAM_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CAPTAIN_PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-12T00:00:00Z");

    private final TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
    private final TeamManualPlayerRepository teamManualPlayerRepository = mock(TeamManualPlayerRepository.class);
    private final TeamRosterLimitRepository teamRosterLimitRepository = mock(TeamRosterLimitRepository.class);
    private final TeamRosterCapacityService service = new TeamRosterCapacityService(
            teamMemberRepository,
            teamManualPlayerRepository,
            teamRosterLimitRepository);

    @Test
    void captainFallbackCountsOwnerWhenLegacyRosterMembershipIsMissing() {
        when(teamRosterLimitRepository.resolveRosterLimit(TEAM_ID)).thenReturn(5);
        when(teamMemberRepository.countActiveByTeamId(TEAM_ID)).thenReturn(4);
        when(teamMemberRepository.existsActive(TEAM_ID, CAPTAIN_PROFILE_ID)).thenReturn(false);

        var capacity = service.resolve(team());

        assertThat(capacity.participantsCount()).isEqualTo(5);
        assertThat(capacity.slotsFilled()).isEqualTo(5);
        assertThat(capacity.slotsRemaining()).isZero();
        assertThat(capacity.isFull()).isTrue();
    }

    @Test
    void captainIsNotCountedTwiceWhenRosterMembershipExists() {
        when(teamRosterLimitRepository.resolveRosterLimit(TEAM_ID)).thenReturn(5);
        when(teamMemberRepository.countActiveByTeamId(TEAM_ID)).thenReturn(5);
        when(teamMemberRepository.existsActive(TEAM_ID, CAPTAIN_PROFILE_ID)).thenReturn(true);

        var capacity = service.resolve(team());

        assertThat(capacity.participantsCount()).isEqualTo(5);
        assertThat(capacity.isFull()).isTrue();
    }

    private static Team team() {
        return new Team(
                TEAM_ID,
                "Ancient Stack",
                "AS",
                "ancient-stack",
                CAPTAIN_PROFILE_ID,
                "Captain",
                "EU",
                null,
                null,
                null,
                NOW,
                NOW);
    }
}
