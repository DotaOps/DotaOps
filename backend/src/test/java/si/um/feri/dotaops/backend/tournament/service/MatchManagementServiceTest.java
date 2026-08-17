package si.um.feri.dotaops.backend.tournament.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.access.AccessDeniedException;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedActor;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.ConflictException;
import si.um.feri.dotaops.backend.common.security.DatabaseActorContext;
import si.um.feri.dotaops.backend.notification.service.NotificationOutboxService;
import si.um.feri.dotaops.backend.tournament.domain.MatchStatus;
import si.um.feri.dotaops.backend.tournament.domain.MatchTeamCaptain;
import si.um.feri.dotaops.backend.tournament.domain.TournamentMatch;
import si.um.feri.dotaops.backend.tournament.dto.CancelMatchRequest;
import si.um.feri.dotaops.backend.tournament.dto.ScheduleMatchRequest;
import si.um.feri.dotaops.backend.tournament.dto.SubmitMatchResultRequest;
import si.um.feri.dotaops.backend.tournament.repository.MatchRepository;
import si.um.feri.dotaops.backend.tournament.repository.TournamentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchManagementServiceTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ORGANIZER_PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PLAYER_PROFILE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID TOURNAMENT_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID MATCH_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID TEAM_A_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID TEAM_B_ID = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID OTHER_TEAM_ID = UUID.fromString("88888888-8888-4888-8888-888888888888");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-06-01T18:00:00Z");
    private static final OffsetDateTime SCHEDULED_AT = OffsetDateTime.parse("2026-06-05T18:00:00Z");

    private final MatchRepository matchRepository = mock(MatchRepository.class);
    private final TournamentRepository tournamentRepository = mock(TournamentRepository.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final DatabaseActorContext databaseActorContext = mock(DatabaseActorContext.class);
    private final MatchAdvancementService matchAdvancementService = mock(MatchAdvancementService.class);
    private final NotificationOutboxService notificationOutboxService = mock(NotificationOutboxService.class);
    private final MatchManagementService service = new MatchManagementService(
            matchRepository,
            tournamentRepository,
            currentUserProvider,
            databaseActorContext,
            matchAdvancementService,
            notificationOutboxService);

    @BeforeEach
    void setUp() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ORGANIZER_PROFILE_ID, ProfileRole.ORGANIZER));
        when(tournamentRepository.canManage(TOURNAMENT_ID, ORGANIZER_PROFILE_ID, false)).thenReturn(true);
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.empty());
    }

    @Test
    void organizerCanScheduleMatch() {
        TournamentMatch existing = match(MatchStatus.READY, 1, 0, 0, null);
        TournamentMatch updated = match(MatchStatus.SCHEDULED, 1, 0, 0, null, SCHEDULED_AT, null, null, null, null);
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(existing));
        when(matchRepository.schedule(MATCH_ID, SCHEDULED_AT)).thenReturn(Optional.of(updated));
        when(matchRepository.findTeamCaptainsByMatchId(MATCH_ID)).thenReturn(List.of(
                new MatchTeamCaptain(TEAM_A_ID, "Team A", PLAYER_PROFILE_ID)));

        var response = service.scheduleMatch(MATCH_ID, new ScheduleMatchRequest(SCHEDULED_AT));

        assertThat(response.status()).isEqualTo("scheduled");
        assertThat(response.scheduledAt()).isEqualTo(SCHEDULED_AT);
        verify(databaseActorContext).apply(actor(ORGANIZER_PROFILE_ID, ProfileRole.ORGANIZER));
        verify(notificationOutboxService).createMatchScheduledNotification(
                eq(updated),
                eq("DotaOps turnir"),
                eq(new MatchTeamCaptain(TEAM_A_ID, "Team A", PLAYER_PROFILE_ID)));
    }

    @Test
    void organizerCanStartScheduledMatch() {
        TournamentMatch existing = match(MatchStatus.SCHEDULED, 1, 0, 0, null);
        TournamentMatch updated = match(MatchStatus.LIVE, 1, 0, 0, null, null, NOW, null, null, null);
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(existing));
        when(matchRepository.start(eq(MATCH_ID), any(OffsetDateTime.class))).thenReturn(Optional.of(updated));

        var response = service.startMatch(MATCH_ID);

        assertThat(response.status()).isEqualTo("live");
        assertThat(response.startedAt()).isEqualTo(NOW);
    }

    @Test
    void matchCannotStartBeforeScheduledTime() {
        TournamentMatch existing = match(
                MatchStatus.SCHEDULED,
                1,
                0,
                0,
                null,
                OffsetDateTime.now().plusDays(1),
                null,
                null,
                null,
                null);
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.startMatch(MATCH_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Match cannot be started before its scheduled time.");
        verify(matchRepository, never()).start(eq(MATCH_ID), any(OffsetDateTime.class));
    }

    @Test
    void organizerCanCancelScheduledMatch() {
        TournamentMatch existing = match(MatchStatus.SCHEDULED, 1, 0, 0, null);
        TournamentMatch updated = match(
                MatchStatus.CANCELLED,
                1,
                0,
                0,
                null,
                null,
                null,
                null,
                NOW,
                "Team did not show up.");
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(existing));
        when(matchRepository.cancel(eq(MATCH_ID), eq("Team did not show up."), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(updated));

        var response = service.cancelMatch(MATCH_ID, new CancelMatchRequest("  Team did not show up.  "));

        assertThat(response.status()).isEqualTo("cancelled");
        assertThat(response.cancellationReason()).isEqualTo("Team did not show up.");
        assertThat(response.cancelledAt()).isEqualTo(NOW);
    }

    @Test
    void cancelledMatchCannotBeStartedOrFinished() {
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(match(MatchStatus.CANCELLED, 1, 0, 0, null)));

        assertThatThrownBy(() -> service.startMatch(MATCH_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Cancelled matches cannot be started.");
        assertThatThrownBy(() -> service.finishMatch(MATCH_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Cancelled matches cannot be finished.");
        verify(matchRepository, never()).start(eq(MATCH_ID), any(OffsetDateTime.class));
        verify(matchRepository, never()).finish(eq(MATCH_ID), any(OffsetDateTime.class));
    }

    @ParameterizedTest
    @CsvSource({
            "1,1,0",
            "3,2,1",
            "5,3,2"
    })
    void organizerCanSubmitValidSeriesResult(int bestOf, int scoreA, int scoreB) {
        TournamentMatch existing = match(MatchStatus.LIVE, bestOf, 0, 0, null);
        TournamentMatch updated = match(MatchStatus.FINISHED, bestOf, scoreA, scoreB, TEAM_A_ID, null, null, NOW, null, null);
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(existing));
        when(matchRepository.submitResult(eq(MATCH_ID), eq(scoreA), eq(scoreB), eq(TEAM_A_ID), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(updated));

        var response = service.submitResult(MATCH_ID, new SubmitMatchResultRequest(scoreA, scoreB, TEAM_A_ID));

        assertThat(response.status()).isEqualTo("finished");
        assertThat(response.scoreA()).isEqualTo(scoreA);
        assertThat(response.scoreB()).isEqualTo(scoreB);
        assertThat(response.winnerTeamId()).isEqualTo(TEAM_A_ID);
        verify(matchAdvancementService).advanceAfterResult(existing, updated, ORGANIZER_PROFILE_ID);
    }

    @Test
    void organizerCanFinishMatchWithExistingValidResult() {
        TournamentMatch existing = match(MatchStatus.LIVE, 3, 2, 1, TEAM_A_ID);
        TournamentMatch updated = match(MatchStatus.FINISHED, 3, 2, 1, TEAM_A_ID, null, NOW, NOW.plusHours(1), null, null);
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(existing));
        when(matchRepository.finish(eq(MATCH_ID), any(OffsetDateTime.class))).thenReturn(Optional.of(updated));

        var response = service.finishMatch(MATCH_ID);

        assertThat(response.status()).isEqualTo("finished");
        assertThat(response.scoreA()).isEqualTo(2);
        assertThat(response.winnerTeamId()).isEqualTo(TEAM_A_ID);
        verify(matchAdvancementService).advanceAfterResult(existing, updated, ORGANIZER_PROFILE_ID);
    }

    @Test
    void finishedMatchResultCanBeCorrectedWhenAdvancementAllowsIt() {
        TournamentMatch existing = match(MatchStatus.FINISHED, 3, 1, 2, TEAM_B_ID);
        TournamentMatch updated = match(MatchStatus.FINISHED, 3, 2, 1, TEAM_A_ID, null, NOW, NOW.plusHours(1), null, null);
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(existing));
        when(matchRepository.submitResult(eq(MATCH_ID), eq(2), eq(1), eq(TEAM_A_ID), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(updated));

        var response = service.submitResult(MATCH_ID, new SubmitMatchResultRequest(2, 1, TEAM_A_ID));

        assertThat(response.status()).isEqualTo("finished");
        assertThat(response.winnerTeamId()).isEqualTo(TEAM_A_ID);
        verify(matchAdvancementService).advanceAfterResult(existing, updated, ORGANIZER_PROFILE_ID);
    }

    @Test
    void bo3RejectsResultThatExceedsRequiredWins() {
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(match(MatchStatus.LIVE, 3, 0, 0, null)));

        assertThatThrownBy(() -> service.submitResult(MATCH_ID, new SubmitMatchResultRequest(3, 0, TEAM_A_ID)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Match result must give the winner exactly 2 game wins.");
        verify(matchRepository, never()).submitResult(
                eq(MATCH_ID),
                anyInt(),
                anyInt(),
                any(UUID.class),
                any(OffsetDateTime.class));
    }

    @Test
    void bo3RejectsTwoTwoResult() {
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(match(MatchStatus.LIVE, 3, 0, 0, null)));

        assertThatThrownBy(() -> service.submitResult(MATCH_ID, new SubmitMatchResultRequest(2, 2, TEAM_A_ID)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Draw results are not supported for this match format.");
    }

    @Test
    void bo3RejectsOneOneDraw() {
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(match(MatchStatus.LIVE, 3, 0, 0, null)));

        assertThatThrownBy(() -> service.submitResult(MATCH_ID, new SubmitMatchResultRequest(1, 1, TEAM_A_ID)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Draw results are not supported for this match format.");
    }

    @Test
    void winnerMustBeOneOfMatchTeams() {
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(match(MatchStatus.LIVE, 1, 0, 0, null)));

        assertThatThrownBy(() -> service.submitResult(MATCH_ID, new SubmitMatchResultRequest(1, 0, OTHER_TEAM_ID)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("winnerTeamId must be one of the teams in the match.");
    }

    @Test
    void winnerMustMatchSubmittedScoreWinner() {
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(match(MatchStatus.LIVE, 3, 0, 0, null)));

        assertThatThrownBy(() -> service.submitResult(MATCH_ID, new SubmitMatchResultRequest(2, 1, TEAM_B_ID)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("winnerTeamId must match the submitted score winner.");
    }

    @Test
    void finishedMatchCannotBeStartedAgain() {
        when(matchRepository.findById(MATCH_ID))
                .thenReturn(Optional.of(match(MatchStatus.FINISHED, 1, 1, 0, TEAM_A_ID)));

        assertThatThrownBy(() -> service.startMatch(MATCH_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Finished matches cannot be started.");
    }

    @Test
    void nonOrganizerCannotSubmitResult() {
        when(currentUserProvider.requireActor()).thenReturn(actor(PLAYER_PROFILE_ID, ProfileRole.PLAYER));
        when(tournamentRepository.canManage(TOURNAMENT_ID, PLAYER_PROFILE_ID, false)).thenReturn(true);
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(match(MatchStatus.LIVE, 1, 0, 0, null)));

        assertThatThrownBy(() -> service.submitResult(MATCH_ID, new SubmitMatchResultRequest(1, 0, TEAM_A_ID)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only the tournament owner, tournament organizers, or admins can manage matches.");
        verify(matchRepository, never()).submitResult(
                eq(MATCH_ID),
                anyInt(),
                anyInt(),
                any(UUID.class),
                any(OffsetDateTime.class));
    }

    private static TournamentMatch match(
            MatchStatus status,
            int bestOf,
            int scoreA,
            int scoreB,
            UUID winnerTeamId
    ) {
        return match(status, bestOf, scoreA, scoreB, winnerTeamId, null, null, null, null, null);
    }

    private static TournamentMatch match(
            MatchStatus status,
            int bestOf,
            int scoreA,
            int scoreB,
            UUID winnerTeamId,
            OffsetDateTime scheduledAt,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            OffsetDateTime cancelledAt,
            String cancellationReason
    ) {
        return new TournamentMatch(
                MATCH_ID,
                TOURNAMENT_ID,
                null,
                1,
                1,
                "Playoffs",
                "Final",
                status,
                TEAM_A_ID,
                "Team A",
                TEAM_B_ID,
                "Team B",
                scoreA,
                scoreB,
                winnerTeamId,
                TEAM_A_ID.equals(winnerTeamId) ? "Team A" : TEAM_B_ID.equals(winnerTeamId) ? "Team B" : null,
                bestOf,
                scheduledAt,
                startedAt,
                finishedAt,
                cancelledAt,
                cancellationReason,
                NOW.minusDays(1),
                NOW);
    }

    private static AuthenticatedActor actor(UUID profileId, ProfileRole role) {
        return new AuthenticatedActor(
                AUTH_USER_ID,
                profileId,
                "user@example.test",
                null,
                role);
    }
}
