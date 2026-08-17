package si.um.feri.dotaops.backend.tournament.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedActor;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.ConflictException;
import si.um.feri.dotaops.backend.common.security.DatabaseActorContext;
import si.um.feri.dotaops.backend.team.domain.Team;
import si.um.feri.dotaops.backend.team.repository.TeamRepository;
import si.um.feri.dotaops.backend.tournament.domain.GroupStanding;
import si.um.feri.dotaops.backend.tournament.domain.Tournament;
import si.um.feri.dotaops.backend.tournament.domain.TournamentFormat;
import si.um.feri.dotaops.backend.tournament.domain.TournamentGroup;
import si.um.feri.dotaops.backend.tournament.domain.TournamentGroupTeam;
import si.um.feri.dotaops.backend.tournament.domain.TournamentSettings;
import si.um.feri.dotaops.backend.tournament.domain.TournamentStatus;
import si.um.feri.dotaops.backend.tournament.dto.AddTeamToGroupRequest;
import si.um.feri.dotaops.backend.tournament.dto.CreateTournamentGroupRequest;
import si.um.feri.dotaops.backend.tournament.repository.AddTournamentGroupTeamCommand;
import si.um.feri.dotaops.backend.tournament.repository.CreateTournamentGroupCommand;
import si.um.feri.dotaops.backend.tournament.repository.TournamentGroupRepository;
import si.um.feri.dotaops.backend.tournament.repository.TournamentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TournamentGroupServiceTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ORGANIZER_PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PLAYER_PROFILE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID TOURNAMENT_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID GROUP_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID OTHER_GROUP_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID TEAM_ID = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID REGISTRATION_ID = UUID.fromString("88888888-8888-4888-8888-888888888888");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-06-01T18:00:00Z");

    private final TournamentGroupRepository groupRepository = mock(TournamentGroupRepository.class);
    private final TournamentRepository tournamentRepository = mock(TournamentRepository.class);
    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final DatabaseActorContext databaseActorContext = mock(DatabaseActorContext.class);
    private final TournamentGroupService service = new TournamentGroupService(
            groupRepository,
            tournamentRepository,
            teamRepository,
            currentUserProvider,
            databaseActorContext);

    @Test
    void organizerCanCreateGroup() {
        AuthenticatedActor actor = actor(ORGANIZER_PROFILE_ID, ProfileRole.ORGANIZER);
        when(currentUserProvider.requireActor()).thenReturn(actor);
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament()));
        when(tournamentRepository.canManage(TOURNAMENT_ID, ORGANIZER_PROFILE_ID, false)).thenReturn(true);
        when(groupRepository.nextSortOrder(TOURNAMENT_ID)).thenReturn(1);
        when(groupRepository.create(any(CreateTournamentGroupCommand.class))).thenReturn(group(GROUP_ID, 1));

        var response = service.createGroup(TOURNAMENT_ID, new CreateTournamentGroupRequest(" Group A ", null));

        assertThat(response.id()).isEqualTo(GROUP_ID);
        assertThat(response.name()).isEqualTo("Group A");
        assertThat(response.sortOrder()).isEqualTo(1);
        verify(databaseActorContext).apply(actor);
        verify(groupRepository).create(new CreateTournamentGroupCommand(TOURNAMENT_ID, "Group A", 1));
    }

    @Test
    void nonOrganizerCannotCreateGroup() {
        when(currentUserProvider.requireActor()).thenReturn(actor(PLAYER_PROFILE_ID, ProfileRole.PLAYER));
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament()));
        when(tournamentRepository.canManage(TOURNAMENT_ID, PLAYER_PROFILE_ID, false)).thenReturn(true);

        assertThatThrownBy(() -> service.createGroup(
                TOURNAMENT_ID,
                new CreateTournamentGroupRequest("Group A", 1)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only the tournament owner, tournament organizers, or admins can manage tournament groups.");
        verify(groupRepository, never()).create(any(CreateTournamentGroupCommand.class));
    }

    @Test
    void organizerCanAddApprovedParticipantToGroup() {
        AuthenticatedActor actor = actor(ORGANIZER_PROFILE_ID, ProfileRole.ORGANIZER);
        when(currentUserProvider.requireActor()).thenReturn(actor);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(GROUP_ID, 1)));
        when(tournamentRepository.canManage(TOURNAMENT_ID, ORGANIZER_PROFILE_ID, false)).thenReturn(true);
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(groupRepository.findApprovedRegistrationId(TOURNAMENT_ID, TEAM_ID)).thenReturn(Optional.of(REGISTRATION_ID));
        when(groupRepository.findAssignmentByTournamentAndTeam(TOURNAMENT_ID, TEAM_ID)).thenReturn(Optional.empty());
        when(groupRepository.addTeam(any(AddTournamentGroupTeamCommand.class))).thenReturn(groupTeam(GROUP_ID));

        var response = service.addTeam(GROUP_ID, new AddTeamToGroupRequest(TEAM_ID, 2));

        assertThat(response.teamId()).isEqualTo(TEAM_ID);
        assertThat(response.registrationId()).isEqualTo(REGISTRATION_ID);
        assertThat(response.seedNumber()).isEqualTo(2);
        verify(databaseActorContext).apply(actor);
        verify(groupRepository).addTeam(new AddTournamentGroupTeamCommand(GROUP_ID, TEAM_ID, REGISTRATION_ID, 2));
    }

    @Test
    void invalidTeamThatIsNotApprovedParticipantCannotBeAdded() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ORGANIZER_PROFILE_ID, ProfileRole.ORGANIZER));
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(GROUP_ID, 1)));
        when(tournamentRepository.canManage(TOURNAMENT_ID, ORGANIZER_PROFILE_ID, false)).thenReturn(true);
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(groupRepository.findApprovedRegistrationId(TOURNAMENT_ID, TEAM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addTeam(GROUP_ID, new AddTeamToGroupRequest(TEAM_ID, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Team must have an approved registration for this tournament before it can be added to a group.");
        verify(groupRepository, never()).addTeam(any(AddTournamentGroupTeamCommand.class));
    }

    @Test
    void duplicateTeamInSameGroupIsRejected() {
        mockGroupAddPreconditions();
        when(groupRepository.findAssignmentByTournamentAndTeam(TOURNAMENT_ID, TEAM_ID))
                .thenReturn(Optional.of(groupTeam(GROUP_ID)));

        assertThatThrownBy(() -> service.addTeam(GROUP_ID, new AddTeamToGroupRequest(TEAM_ID, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Team is already assigned to this group.");
    }

    @Test
    void teamAlreadyAssignedToAnotherGroupIsRejected() {
        mockGroupAddPreconditions();
        when(groupRepository.findAssignmentByTournamentAndTeam(TOURNAMENT_ID, TEAM_ID))
                .thenReturn(Optional.of(groupTeam(OTHER_GROUP_ID)));

        assertThatThrownBy(() -> service.addTeam(GROUP_ID, new AddTeamToGroupRequest(TEAM_ID, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Team is already assigned to another group in this tournament.");
    }

    @Test
    void publicStandingsExposeGameAndMatchFields() {
        when(groupRepository.publicGroupExists(GROUP_ID)).thenReturn(true);
        when(groupRepository.findStandingsByGroupId(GROUP_ID)).thenReturn(List.of(standing()));

        var standings = service.getPublicStandings(GROUP_ID);

        assertThat(standings).hasSize(1);
        assertThat(standings.getFirst().groupName()).isEqualTo("Group A");
        assertThat(standings.getFirst().matchWins()).isOne();
        assertThat(standings.getFirst().gameWins()).isEqualTo(2);
        assertThat(standings.getFirst().gameLosses()).isEqualTo(1);
        assertThat(standings.getFirst().gameDiff()).isOne();
        assertThat(standings.getFirst().points()).isEqualTo(3);
    }

    @Test
    void organizerCanListPrivateTournamentStandings() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ORGANIZER_PROFILE_ID, ProfileRole.ORGANIZER));
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament()));
        when(tournamentRepository.canManage(TOURNAMENT_ID, ORGANIZER_PROFILE_ID, false)).thenReturn(true);
        when(groupRepository.findOrganizerStandingsByTournamentId(TOURNAMENT_ID)).thenReturn(List.of(standing()));

        var standings = service.listOrganizerStandings(TOURNAMENT_ID);

        assertThat(standings).hasSize(1);
        assertThat(standings.getFirst().groupId()).isEqualTo(GROUP_ID);
        assertThat(standings.getFirst().groupName()).isEqualTo("Group A");
        assertThat(standings.getFirst().points()).isEqualTo(3);
        verify(groupRepository).findOrganizerStandingsByTournamentId(TOURNAMENT_ID);
    }

    @Test
    void nonOrganizerCannotListPrivateTournamentStandings() {
        when(currentUserProvider.requireActor()).thenReturn(actor(PLAYER_PROFILE_ID, ProfileRole.PLAYER));
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament()));
        when(tournamentRepository.canManage(TOURNAMENT_ID, PLAYER_PROFILE_ID, false)).thenReturn(true);

        assertThatThrownBy(() -> service.listOrganizerStandings(TOURNAMENT_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only the tournament owner, tournament organizers, or admins can manage tournament groups.");
        verify(groupRepository, never()).findOrganizerStandingsByTournamentId(any());
    }

    private void mockGroupAddPreconditions() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ORGANIZER_PROFILE_ID, ProfileRole.ORGANIZER));
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(GROUP_ID, 1)));
        when(tournamentRepository.canManage(TOURNAMENT_ID, ORGANIZER_PROFILE_ID, false)).thenReturn(true);
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(groupRepository.findApprovedRegistrationId(TOURNAMENT_ID, TEAM_ID)).thenReturn(Optional.of(REGISTRATION_ID));
    }

    private static AuthenticatedActor actor(UUID profileId, ProfileRole role) {
        return new AuthenticatedActor(
                AUTH_USER_ID,
                profileId,
                "user@example.test",
                null,
                role);
    }

    private static Tournament tournament() {
        return new Tournament(
                TOURNAMENT_ID,
                "group-stage-cup",
                "Group Stage Cup",
                TournamentStatus.PUBLISHED,
                TournamentFormat.GROUPS_PLAYOFF,
                ORGANIZER_PROFILE_ID,
                "Organizer",
                "Qualifier",
                "Rules",
                "TBD",
                8,
                NOW.plusDays(3),
                NOW.plusDays(4),
                NOW.minusDays(2),
                NOW.plusDays(1),
                true,
                AUTH_USER_ID,
                "UTC",
                null,
                null,
                NOW.minusDays(1),
                new TournamentSettings(8, 2, 5, 1, TournamentFormat.GROUPS_PLAYOFF, false, true),
                0,
                NOW.minusDays(5),
                NOW.minusDays(1));
    }

    private static TournamentGroup group(UUID groupId, int sortOrder) {
        return new TournamentGroup(
                groupId,
                TOURNAMENT_ID,
                "Group A",
                sortOrder,
                NOW.minusHours(1),
                NOW);
    }

    private static Team team() {
        return new Team(
                TEAM_ID,
                "Radiant Five",
                "R5",
                "radiant-five",
                PLAYER_PROFILE_ID,
                "Captain",
                "EU",
                null,
                null,
                AUTH_USER_ID,
                NOW.minusDays(10),
                NOW.minusDays(1));
    }

    private static TournamentGroupTeam groupTeam(UUID groupId) {
        return new TournamentGroupTeam(
                UUID.fromString("99999999-9999-4999-8999-999999999999"),
                groupId,
                TOURNAMENT_ID,
                TEAM_ID,
                "Radiant Five",
                "R5",
                "radiant-five",
                REGISTRATION_ID,
                2,
                NOW.minusMinutes(10),
                NOW);
    }

    private static GroupStanding standing() {
        return new GroupStanding(
                GROUP_ID,
                "Group A",
                TOURNAMENT_ID,
                TEAM_ID,
                "Radiant Five",
                1,
                1,
                0,
                0,
                2,
                1,
                1,
                3,
                1);
    }
}
