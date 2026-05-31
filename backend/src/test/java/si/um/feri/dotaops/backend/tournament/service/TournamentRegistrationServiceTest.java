package si.um.feri.dotaops.backend.tournament.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedActor;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.ConflictException;
import si.um.feri.dotaops.backend.common.security.DatabaseActorContext;
import si.um.feri.dotaops.backend.notification.service.NotificationOutboxService;
import si.um.feri.dotaops.backend.team.domain.Team;
import si.um.feri.dotaops.backend.team.domain.TeamMemberRole;
import si.um.feri.dotaops.backend.team.repository.TeamMemberRepository;
import si.um.feri.dotaops.backend.team.repository.TeamRepository;
import si.um.feri.dotaops.backend.tournament.domain.Tournament;
import si.um.feri.dotaops.backend.tournament.domain.TournamentFormat;
import si.um.feri.dotaops.backend.tournament.domain.TournamentRegistration;
import si.um.feri.dotaops.backend.tournament.domain.TournamentRegistrationMember;
import si.um.feri.dotaops.backend.tournament.domain.TournamentRegistrationStatus;
import si.um.feri.dotaops.backend.tournament.domain.TournamentSettings;
import si.um.feri.dotaops.backend.tournament.domain.TournamentStatus;
import si.um.feri.dotaops.backend.tournament.dto.CreateTournamentRegistrationRequest;
import si.um.feri.dotaops.backend.tournament.dto.ReviewTournamentRegistrationRequest;
import si.um.feri.dotaops.backend.tournament.repository.CreateTournamentRegistrationCommand;
import si.um.feri.dotaops.backend.tournament.repository.TournamentRegistrationRepository;
import si.um.feri.dotaops.backend.tournament.repository.TournamentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TournamentRegistrationServiceTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CAPTAIN_PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID OTHER_PROFILE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID ORGANIZER_PROFILE_ID = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID TOURNAMENT_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID TEAM_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID REGISTRATION_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final OffsetDateTime NOW = OffsetDateTime.now(java.time.ZoneOffset.UTC);

    private final TournamentRegistrationRepository registrationRepository = mock(TournamentRegistrationRepository.class);
    private final TournamentRepository tournamentRepository = mock(TournamentRepository.class);
    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final DatabaseActorContext databaseActorContext = mock(DatabaseActorContext.class);
    private final NotificationOutboxService notificationOutboxService = mock(NotificationOutboxService.class);
    private final TournamentRegistrationService service = new TournamentRegistrationService(
            registrationRepository,
            tournamentRepository,
            teamRepository,
            teamMemberRepository,
            currentUserProvider,
            databaseActorContext,
            notificationOutboxService);

    @Test
    void captainCanRegisterTeamAndRosterSnapshotUsesTournamentTeamSize() {
        AuthenticatedActor actor = actor(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER);
        when(currentUserProvider.requireActor()).thenReturn(actor);
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(openTournament()));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team(CAPTAIN_PROFILE_ID)));
        when(registrationRepository.countActiveRosterMembers(TEAM_ID)).thenReturn(5);
        when(registrationRepository.create(any(CreateTournamentRegistrationCommand.class), eq(5)))
                .thenReturn(registration(TournamentRegistrationStatus.PENDING));
        when(registrationRepository.findMembers(REGISTRATION_ID)).thenReturn(List.of());

        var response = service.registerTeam(TOURNAMENT_ID, new CreateTournamentRegistrationRequest(
                TEAM_ID,
                "Ready",
                "Captain@Example.test"));

        assertThat(response.id()).isEqualTo(REGISTRATION_ID);
        assertThat(response.status()).isEqualTo("pending");
        verify(databaseActorContext).apply(actor);
        verify(registrationRepository).create(any(CreateTournamentRegistrationCommand.class), eq(5));
        verify(notificationOutboxService).createTeamApplicationSubmittedNotification(
                any(Tournament.class),
                any(TournamentRegistration.class));
    }

    @Test
    void nonCaptainCannotRegisterTeam() {
        when(currentUserProvider.requireActor()).thenReturn(actor(OTHER_PROFILE_ID, ProfileRole.PLAYER));
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(openTournament()));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team(CAPTAIN_PROFILE_ID)));

        assertThatThrownBy(() -> service.registerTeam(TOURNAMENT_ID, new CreateTournamentRegistrationRequest(
                TEAM_ID,
                null,
                null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only the team captain can register this team for a tournament.");
    }

    @Test
    void duplicateTeamRegistrationIsRejected() {
        when(currentUserProvider.requireActor()).thenReturn(actor(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(openTournament()));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team(CAPTAIN_PROFILE_ID)));
        when(registrationRepository.existsByTournamentIdAndTeamId(TOURNAMENT_ID, TEAM_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.registerTeam(TOURNAMENT_ID, new CreateTournamentRegistrationRequest(
                TEAM_ID,
                null,
                null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Team is already registered for this tournament.");
        verify(registrationRepository, never()).create(any(CreateTournamentRegistrationCommand.class), eq(5));
    }

    @Test
    void duplicateRaceIsRejectedByDatabaseConstraint() {
        when(currentUserProvider.requireActor()).thenReturn(actor(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(openTournament()));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team(CAPTAIN_PROFILE_ID)));
        when(registrationRepository.existsByTournamentIdAndTeamId(TOURNAMENT_ID, TEAM_ID)).thenReturn(false);
        when(registrationRepository.countActiveRosterMembers(TEAM_ID)).thenReturn(5);
        when(registrationRepository.create(any(CreateTournamentRegistrationCommand.class), eq(5)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate",
                        new RuntimeException("tournament_registrations_tournament_id_team_id_key")));

        assertThatThrownBy(() -> service.registerTeam(TOURNAMENT_ID, new CreateTournamentRegistrationRequest(
                TEAM_ID,
                null,
                null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Team is already registered for this tournament.");
    }

    @Test
    void registrationRequiresFullActiveRoster() {
        when(currentUserProvider.requireActor()).thenReturn(actor(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(openTournament()));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team(CAPTAIN_PROFILE_ID)));
        when(registrationRepository.countActiveRosterMembers(TEAM_ID)).thenReturn(4);

        assertThatThrownBy(() -> service.registerTeam(TOURNAMENT_ID, new CreateTournamentRegistrationRequest(
                TEAM_ID,
                null,
                null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Team must have at least 5 active roster participants before registration.");
    }

    @Test
    void oneVsOneTournamentAllowsSingleRosterParticipant() {
        TournamentSettings settings = new TournamentSettings(
                8,
                2,
                1,
                1,
                TournamentFormat.SINGLE_ELIMINATION,
                false,
                true);
        when(currentUserProvider.requireActor()).thenReturn(actor(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament(
                TournamentStatus.PUBLISHED,
                settings,
                NOW.minusDays(1),
                NOW.plusDays(1))));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team(CAPTAIN_PROFILE_ID)));
        when(registrationRepository.countActiveRosterMembers(TEAM_ID)).thenReturn(1);
        when(registrationRepository.create(any(CreateTournamentRegistrationCommand.class), eq(1)))
                .thenReturn(registration(TournamentRegistrationStatus.PENDING));
        when(registrationRepository.findMembers(REGISTRATION_ID)).thenReturn(List.of());

        service.registerTeam(TOURNAMENT_ID, new CreateTournamentRegistrationRequest(TEAM_ID, null, null));

        verify(registrationRepository).create(any(CreateTournamentRegistrationCommand.class), eq(1));
    }

    @Test
    void threeVsThreeTournamentRejectsRosterAboveTeamSize() {
        TournamentSettings settings = new TournamentSettings(
                8,
                2,
                3,
                1,
                TournamentFormat.SINGLE_ELIMINATION,
                false,
                true);
        when(currentUserProvider.requireActor()).thenReturn(actor(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament(
                TournamentStatus.PUBLISHED,
                settings,
                NOW.minusDays(1),
                NOW.plusDays(1))));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team(CAPTAIN_PROFILE_ID)));
        when(registrationRepository.countActiveRosterMembers(TEAM_ID)).thenReturn(4);

        assertThatThrownBy(() -> service.registerTeam(TOURNAMENT_ID, new CreateTournamentRegistrationRequest(
                TEAM_ID,
                null,
                null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Team cannot have more than 3 active roster participants for this tournament.");

        verify(registrationRepository, never()).create(any(CreateTournamentRegistrationCommand.class), eq(3));
    }

    @Test
    void manualPlayersAreIncludedInRosterSizeValidation() {
        TournamentSettings settings = new TournamentSettings(
                8,
                2,
                1,
                1,
                TournamentFormat.SINGLE_ELIMINATION,
                false,
                true);
        when(currentUserProvider.requireActor()).thenReturn(actor(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament(
                TournamentStatus.PUBLISHED,
                settings,
                NOW.minusDays(1),
                NOW.plusDays(1))));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team(CAPTAIN_PROFILE_ID)));
        when(registrationRepository.countActiveRosterMembers(TEAM_ID)).thenReturn(2);

        assertThatThrownBy(() -> service.registerTeam(TOURNAMENT_ID, new CreateTournamentRegistrationRequest(
                TEAM_ID,
                null,
                null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Team cannot have more than 1 active roster participants for this tournament.");
    }

    @Test
    void teamRegistrationStatusUsesStoredRosterSnapshotAfterRosterChanges() {
        when(currentUserProvider.requireActor()).thenReturn(actor(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team(CAPTAIN_PROFILE_ID)));
        when(registrationRepository.findByTeamId(TEAM_ID))
                .thenReturn(List.of(registration(TournamentRegistrationStatus.PENDING)));
        when(registrationRepository.findMembers(REGISTRATION_ID))
                .thenReturn(List.of(snapshotMember(CAPTAIN_PROFILE_ID)));

        var response = service.listTeamRegistrations(TEAM_ID);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().members()).hasSize(1);
        assertThat(response.getFirst().members().getFirst().profileId()).isEqualTo(CAPTAIN_PROFILE_ID);
        verify(registrationRepository, never()).create(any(CreateTournamentRegistrationCommand.class), eq(5));
    }

    @Test
    void organizerCanApproveRegistration() {
        mockOrganizerReview(TournamentRegistrationStatus.PENDING);
        when(registrationRepository.countApprovedRegistrations(TOURNAMENT_ID, REGISTRATION_ID)).thenReturn(0L);
        when(registrationRepository.updateStatus(
                REGISTRATION_ID,
                TOURNAMENT_ID,
                TournamentRegistrationStatus.APPROVED,
                ORGANIZER_PROFILE_ID,
                1))
                .thenReturn(Optional.of(registration(TournamentRegistrationStatus.APPROVED)));
        when(registrationRepository.findMembers(REGISTRATION_ID)).thenReturn(List.of());

        var response = service.approveRegistration(
                TOURNAMENT_ID,
                REGISTRATION_ID,
                new ReviewTournamentRegistrationRequest(1));

        assertThat(response.status()).isEqualTo("approved");
        verify(databaseActorContext).apply(actor(ORGANIZER_PROFILE_ID, ProfileRole.ORGANIZER));
        verify(notificationOutboxService).createTeamApplicationApprovedNotification(any(TournamentRegistration.class));
    }

    @Test
    void organizerCanRejectRegistration() {
        mockOrganizerReview(TournamentRegistrationStatus.PENDING);
        when(registrationRepository.updateStatus(
                REGISTRATION_ID,
                TOURNAMENT_ID,
                TournamentRegistrationStatus.REJECTED,
                ORGANIZER_PROFILE_ID,
                null))
                .thenReturn(Optional.of(registration(TournamentRegistrationStatus.REJECTED)));
        when(registrationRepository.findMembers(REGISTRATION_ID)).thenReturn(List.of());

        var response = service.rejectRegistration(TOURNAMENT_ID, REGISTRATION_ID);

        assertThat(response.status()).isEqualTo("rejected");
        verify(notificationOutboxService).createTeamApplicationRejectedNotification(any(TournamentRegistration.class));
    }

    @Test
    void organizerCanWaitlistRegistration() {
        mockOrganizerReview(TournamentRegistrationStatus.PENDING);
        when(registrationRepository.updateStatus(
                REGISTRATION_ID,
                TOURNAMENT_ID,
                TournamentRegistrationStatus.WAITLISTED,
                ORGANIZER_PROFILE_ID,
                null))
                .thenReturn(Optional.of(registration(TournamentRegistrationStatus.WAITLISTED)));
        when(registrationRepository.findMembers(REGISTRATION_ID)).thenReturn(List.of());

        var response = service.waitlistRegistration(TOURNAMENT_ID, REGISTRATION_ID);

        assertThat(response.status()).isEqualTo("waitlisted");
    }

    @Test
    void nonOrganizerCannotChangeRegistrationStatus() {
        when(currentUserProvider.requireActor()).thenReturn(actor(OTHER_PROFILE_ID, ProfileRole.PLAYER));
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(openTournament()));
        when(registrationRepository.findByIdAndTournamentId(REGISTRATION_ID, TOURNAMENT_ID))
                .thenReturn(Optional.of(registration(TournamentRegistrationStatus.PENDING)));
        when(tournamentRepository.canManage(TOURNAMENT_ID, OTHER_PROFILE_ID, false)).thenReturn(false);

        assertThatThrownBy(() -> service.rejectRegistration(TOURNAMENT_ID, REGISTRATION_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only the tournament owner, tournament organizers, or admins can manage registrations.");
    }

    @Test
    void approveRejectsWhenTournamentIsAlreadyFull() {
        mockOrganizerReview(TournamentRegistrationStatus.PENDING);
        when(registrationRepository.countApprovedRegistrations(TOURNAMENT_ID, REGISTRATION_ID)).thenReturn(8L);

        assertThatThrownBy(() -> service.approveRegistration(
                TOURNAMENT_ID,
                REGISTRATION_ID,
                new ReviewTournamentRegistrationRequest(1)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Tournament already has the maximum number of approved teams.");
    }

    @Test
    void approvedCaptainCanCheckInWhenWindowIsOpen() {
        AuthenticatedActor actor = actor(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER);
        when(currentUserProvider.requireActor()).thenReturn(actor);
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament(
                TournamentStatus.PUBLISHED,
                settings(true),
                NOW.minusHours(1),
                NOW.plusHours(1))));
        when(registrationRepository.findByIdAndTournamentId(REGISTRATION_ID, TOURNAMENT_ID))
                .thenReturn(Optional.of(registration(TournamentRegistrationStatus.APPROVED)));
        when(registrationRepository.checkIn(REGISTRATION_ID, TOURNAMENT_ID))
                .thenReturn(Optional.of(registration(TournamentRegistrationStatus.APPROVED)));
        when(registrationRepository.findMembers(REGISTRATION_ID)).thenReturn(List.of());

        var response = service.checkInRegistration(TOURNAMENT_ID, REGISTRATION_ID);

        assertThat(response.status()).isEqualTo("approved");
        verify(databaseActorContext).apply(actor);
    }

    @Test
    void checkInOutsideWindowIsRejected() {
        when(currentUserProvider.requireActor()).thenReturn(actor(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament(
                TournamentStatus.PUBLISHED,
                settings(true),
                NOW.plusHours(1),
                NOW.plusHours(2))));
        when(registrationRepository.findByIdAndTournamentId(REGISTRATION_ID, TOURNAMENT_ID))
                .thenReturn(Optional.of(registration(TournamentRegistrationStatus.APPROVED)));

        assertThatThrownBy(() -> service.checkInRegistration(TOURNAMENT_ID, REGISTRATION_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tournament check-in is not open yet.");
    }

    @Test
    void rejectedRegistrationCannotCheckIn() {
        when(currentUserProvider.requireActor()).thenReturn(actor(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament(
                TournamentStatus.PUBLISHED,
                settings(true),
                NOW.minusHours(1),
                NOW.plusHours(1))));
        when(registrationRepository.findByIdAndTournamentId(REGISTRATION_ID, TOURNAMENT_ID))
                .thenReturn(Optional.of(registration(TournamentRegistrationStatus.REJECTED)));

        assertThatThrownBy(() -> service.checkInRegistration(TOURNAMENT_ID, REGISTRATION_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only approved registrations can check in.");
    }

    @Test
    void organizerCanSeeRegistrationsForManagedTournament() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ORGANIZER_PROFILE_ID, ProfileRole.ORGANIZER));
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(openTournament()));
        when(tournamentRepository.canManage(TOURNAMENT_ID, ORGANIZER_PROFILE_ID, false)).thenReturn(true);
        when(registrationRepository.findByTournamentId(TOURNAMENT_ID, TournamentRegistrationStatus.PENDING))
                .thenReturn(List.of(registration(TournamentRegistrationStatus.PENDING)));
        when(registrationRepository.findMembers(REGISTRATION_ID)).thenReturn(List.of());

        var response = service.listOrganizerRegistrations(TOURNAMENT_ID, "pending");

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().status()).isEqualTo("pending");
    }

    @Test
    void teamCaptainCanSeeTeamRegistrationStatus() {
        when(currentUserProvider.requireActor()).thenReturn(actor(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team(CAPTAIN_PROFILE_ID)));
        when(registrationRepository.findByTeamId(TEAM_ID))
                .thenReturn(List.of(registration(TournamentRegistrationStatus.WAITLISTED)));
        when(registrationRepository.findMembers(REGISTRATION_ID)).thenReturn(List.of());

        var response = service.listTeamRegistrations(TEAM_ID);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().status()).isEqualTo("waitlisted");
    }

    @Test
    void activePlayerCanSeeTeamRegistrationStatus() {
        when(currentUserProvider.requireActor()).thenReturn(actor(OTHER_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team(CAPTAIN_PROFILE_ID)));
        when(teamMemberRepository.existsActive(TEAM_ID, OTHER_PROFILE_ID)).thenReturn(true);
        when(registrationRepository.findByTeamId(TEAM_ID))
                .thenReturn(List.of(registration(TournamentRegistrationStatus.APPROVED)));
        when(registrationRepository.findMembers(REGISTRATION_ID)).thenReturn(List.of());

        var response = service.listTeamRegistrations(TEAM_ID);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().status()).isEqualTo("approved");
    }

    @Test
    void nonMemberCannotSeeTeamRegistrationStatus() {
        when(currentUserProvider.requireActor()).thenReturn(actor(OTHER_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team(CAPTAIN_PROFILE_ID)));
        when(teamMemberRepository.existsActive(TEAM_ID, OTHER_PROFILE_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.listTeamRegistrations(TEAM_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only active team members can view this team's tournament registrations.");
    }

    @Test
    void checkedInRegistrationCannotBeReviewedAgain() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ORGANIZER_PROFILE_ID, ProfileRole.ORGANIZER));
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(openTournament()));
        when(registrationRepository.findByIdAndTournamentId(REGISTRATION_ID, TOURNAMENT_ID))
                .thenReturn(Optional.of(registration(TournamentRegistrationStatus.APPROVED, NOW.minusMinutes(5))));
        when(tournamentRepository.canManage(TOURNAMENT_ID, ORGANIZER_PROFILE_ID, false)).thenReturn(true);

        assertThatThrownBy(() -> service.rejectRegistration(TOURNAMENT_ID, REGISTRATION_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Checked-in registrations can no longer be reviewed.");
    }

    private void mockOrganizerReview(TournamentRegistrationStatus currentStatus) {
        when(currentUserProvider.requireActor()).thenReturn(actor(ORGANIZER_PROFILE_ID, ProfileRole.ORGANIZER));
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(openTournament()));
        when(registrationRepository.findByIdAndTournamentId(REGISTRATION_ID, TOURNAMENT_ID))
                .thenReturn(Optional.of(registration(currentStatus)));
        when(tournamentRepository.canManage(TOURNAMENT_ID, ORGANIZER_PROFILE_ID, false)).thenReturn(true);
    }

    private static AuthenticatedActor actor(UUID profileId, ProfileRole role) {
        return new AuthenticatedActor(
                AUTH_USER_ID,
                profileId,
                "user@example.test",
                null,
                role);
    }

    private static Team team(UUID captainProfileId) {
        return new Team(
                TEAM_ID,
                "Radiant Five",
                "R5",
                "radiant-five",
                captainProfileId,
                "Captain",
                "EU",
                null,
                null,
                AUTH_USER_ID,
                NOW.minusDays(10),
                NOW.minusDays(1));
    }

    private static TournamentSettings settings(boolean checkInEnabled) {
        return new TournamentSettings(
                8,
                2,
                5,
                1,
                TournamentFormat.SINGLE_ELIMINATION,
                checkInEnabled,
                true);
    }

    private static Tournament openTournament() {
        return tournament(
                TournamentStatus.PUBLISHED,
                settings(false),
                NOW.minusDays(1),
                NOW.plusDays(1));
    }

    private static Tournament tournament(
            TournamentStatus status,
            TournamentSettings settings,
            OffsetDateTime checkInOpensAt,
            OffsetDateTime checkInClosesAt
    ) {
        return new Tournament(
                TOURNAMENT_ID,
                "mid-wars-open",
                "Mid Wars Open",
                status,
                settings.format(),
                ORGANIZER_PROFILE_ID,
                "Organizer",
                "Qualifier",
                "Rules",
                "TBD",
                settings.maxTeams(),
                NOW.plusDays(2),
                NOW.plusDays(3),
                NOW.minusDays(2),
                NOW.plusDays(1),
                true,
                AUTH_USER_ID,
                "UTC",
                checkInOpensAt,
                checkInClosesAt,
                NOW.minusDays(5),
                settings,
                0,
                NOW.minusDays(10),
                NOW.minusDays(1));
    }

    private static TournamentRegistration registration(TournamentRegistrationStatus status) {
        return registration(status, null);
    }

    private static TournamentRegistration registration(TournamentRegistrationStatus status, OffsetDateTime checkedInAt) {
        return new TournamentRegistration(
                REGISTRATION_ID,
                TOURNAMENT_ID,
                "mid-wars-open",
                "Mid Wars Open",
                TEAM_ID,
                "Radiant Five",
                "R5",
                "radiant-five",
                CAPTAIN_PROFILE_ID,
                "Captain",
                status,
                "Ready",
                null,
                null,
                null,
                null,
                checkedInAt,
                "captain@example.test",
                NOW.minusHours(2),
                NOW.minusHours(1));
    }

    private static TournamentRegistrationMember snapshotMember(UUID profileId) {
        return new TournamentRegistrationMember(
                UUID.fromString("99999999-9999-4999-8999-999999999999"),
                REGISTRATION_ID,
                profileId,
                "captain",
                "Captain",
                null,
                UUID.fromString("88888888-8888-4888-8888-888888888888"),
                TeamMemberRole.CARRY,
                true,
                NOW.minusHours(2),
                NOW.minusHours(2));
    }
}
