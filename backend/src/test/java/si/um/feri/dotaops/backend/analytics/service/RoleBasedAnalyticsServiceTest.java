package si.um.feri.dotaops.backend.analytics.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import si.um.feri.dotaops.backend.analytics.domain.AnalyticsFilters;
import si.um.feri.dotaops.backend.analytics.repository.RoleBasedAnalyticsRepository;
import si.um.feri.dotaops.backend.analytics.web.AnalyticsMatchHistoryResponse;
import si.um.feri.dotaops.backend.auth.domain.AuthenticatedActor;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.team.domain.Team;
import si.um.feri.dotaops.backend.team.repository.TeamMemberRepository;
import si.um.feri.dotaops.backend.team.repository.TeamRepository;
import si.um.feri.dotaops.backend.tournament.repository.TournamentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleBasedAnalyticsServiceTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID TEAM_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID TOURNAMENT_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID MATCH_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID MATCH_GAME_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID OPPONENT_TEAM_ID = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-12T00:00:00Z");
    private static final OffsetDateTime PLAYED_AT = OffsetDateTime.parse("2026-05-11T18:30:00Z");

    private final AnalyticsQueryService analyticsQueryService = mock(AnalyticsQueryService.class);
    private final RoleBasedAnalyticsRepository roleBasedAnalyticsRepository = mock(RoleBasedAnalyticsRepository.class);
    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
    private final TournamentRepository tournamentRepository = mock(TournamentRepository.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final RoleBasedAnalyticsService service = new RoleBasedAnalyticsService(
            analyticsQueryService,
            roleBasedAnalyticsRepository,
            teamRepository,
            teamMemberRepository,
            tournamentRepository,
            currentUserProvider);

    @Test
    void playerAnalyticsAreScopedToCurrentProfileAndHaveStableEmptyHistory() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(analyticsQueryService.recentMatchesForPlayer(eq(PROFILE_ID), any(AnalyticsFilters.class), eq(false)))
                .thenReturn(List.of());

        var response = service.currentPlayerAnalytics(new AnalyticsFilters(
                null,
                null,
                PROFILE_ID,
                null,
                OffsetDateTime.parse("2026-05-01T00:00:00Z"),
                OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                25));

        ArgumentCaptor<AnalyticsFilters> filters = ArgumentCaptor.forClass(AnalyticsFilters.class);
        verify(analyticsQueryService).protectedPlayerMetrics(filters.capture());
        assertThat(filters.getValue().profileId()).isEqualTo(PROFILE_ID);
        assertThat(filters.getValue().from()).isEqualTo(OffsetDateTime.parse("2026-05-01T00:00:00Z"));
        assertThat(filters.getValue().to()).isEqualTo(OffsetDateTime.parse("2026-06-01T00:00:00Z"));
        assertThat(filters.getValue().limit()).isEqualTo(25);
        verify(analyticsQueryService).recentMatchesForPlayer(eq(PROFILE_ID), any(AnalyticsFilters.class), eq(false));
        assertThat(response.metrics()).isEmpty();
        assertThat(response.heroPerformance()).isEmpty();
        assertThat(response.matchHistory()).isEmpty();
    }

    @Test
    void playerAnalyticsReturnRecentMatchHistoryWhenDataExists() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(analyticsQueryService.recentMatchesForPlayer(eq(PROFILE_ID), any(AnalyticsFilters.class), eq(false)))
                .thenReturn(List.of(matchHistory()));

        var response = service.currentPlayerAnalytics(new AnalyticsFilters(
                TOURNAMENT_ID,
                TEAM_ID,
                PROFILE_ID,
                null,
                OffsetDateTime.parse("2026-05-01T00:00:00Z"),
                OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                25));

        ArgumentCaptor<AnalyticsFilters> filters = ArgumentCaptor.forClass(AnalyticsFilters.class);
        verify(analyticsQueryService).recentMatchesForPlayer(eq(PROFILE_ID), filters.capture(), eq(false));
        assertThat(filters.getValue().tournamentId()).isEqualTo(TOURNAMENT_ID);
        assertThat(filters.getValue().teamId()).isEqualTo(TEAM_ID);
        assertThat(filters.getValue().profileId()).isEqualTo(PROFILE_ID);
        assertThat(filters.getValue().limit()).isEqualTo(25);
        assertThat(response.matchHistory()).singleElement().satisfies(match -> {
            assertThat(match.matchId()).isEqualTo(MATCH_ID);
            assertThat(match.matchGameId()).isEqualTo(MATCH_GAME_ID);
            assertThat(match.dotaMatchId()).isEqualTo("7894561230");
            assertThat(match.tournamentId()).isEqualTo(TOURNAMENT_ID);
            assertThat(match.tournamentName()).isEqualTo("Mid Wars");
            assertThat(match.playedAt()).isEqualTo(PLAYED_AT);
            assertThat(match.teamAId()).isEqualTo(TEAM_ID);
            assertThat(match.teamAName()).isEqualTo("Radiant Ops");
            assertThat(match.teamBId()).isEqualTo(OPPONENT_TEAM_ID);
            assertThat(match.teamBName()).isEqualTo("Dire Ops");
            assertThat(match.winnerTeamId()).isEqualTo(TEAM_ID);
        });
    }

    @Test
    void playerAnalyticsRejectAnotherProfileFilter() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));

        assertThatThrownBy(() -> service.currentPlayerAnalytics(new AnalyticsFilters(
                null,
                null,
                UUID.fromString("99999999-9999-4999-8999-999999999999"),
                null,
                10)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Players can only view their own private analytics.");
    }

    @Test
    void currentTeamAnalyticsReturnStableEmptyStateWithoutTeam() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(teamRepository.findCurrentTeamForProfile(PROFILE_ID)).thenReturn(Optional.empty());

        var response = service.currentTeamAnalytics();

        assertThat(response.team()).isNull();
        assertThat(response.teamSummary()).isEmpty();
        assertThat(response.rosterPerformance()).isEmpty();
        assertThat(response.recentTeamMatches()).isEmpty();
    }

    @Test
    void currentTeamAnalyticsReturnEmptyMatchHistoryWhenNoMatchDataExists() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(teamRepository.findCurrentTeamForProfile(PROFILE_ID)).thenReturn(Optional.of(team()));
        when(analyticsQueryService.recentMatchesForTeam(eq(TEAM_ID), any(AnalyticsFilters.class), eq(false)))
                .thenReturn(List.of());

        var response = service.currentTeamAnalytics();

        assertThat(response.team()).isNotNull();
        assertThat(response.teamSummary()).isEmpty();
        assertThat(response.rosterPerformance()).isEmpty();
        assertThat(response.recentTeamMatches()).isEmpty();
    }

    @Test
    void currentTeamAnalyticsReturnRecentMatchHistoryWhenDataExists() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(teamRepository.findCurrentTeamForProfile(PROFILE_ID)).thenReturn(Optional.of(team()));
        when(analyticsQueryService.recentMatchesForTeam(eq(TEAM_ID), any(AnalyticsFilters.class), eq(false)))
                .thenReturn(List.of(matchHistory()));

        var response = service.currentTeamAnalytics(new AnalyticsFilters(
                TOURNAMENT_ID,
                TEAM_ID,
                PROFILE_ID,
                null,
                OffsetDateTime.parse("2026-05-01T00:00:00Z"),
                OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                25));

        ArgumentCaptor<AnalyticsFilters> filters = ArgumentCaptor.forClass(AnalyticsFilters.class);
        verify(analyticsQueryService).recentMatchesForTeam(eq(TEAM_ID), filters.capture(), eq(false));
        assertThat(filters.getValue().tournamentId()).isEqualTo(TOURNAMENT_ID);
        assertThat(filters.getValue().teamId()).isEqualTo(TEAM_ID);
        assertThat(filters.getValue().profileId()).isEqualTo(PROFILE_ID);
        assertThat(filters.getValue().limit()).isEqualTo(25);
        assertThat(response.recentTeamMatches()).singleElement().satisfies(match -> {
            assertThat(match.matchId()).isEqualTo(MATCH_ID);
            assertThat(match.matchGameId()).isEqualTo(MATCH_GAME_ID);
            assertThat(match.dotaMatchId()).isEqualTo("7894561230");
            assertThat(match.tournamentId()).isEqualTo(TOURNAMENT_ID);
            assertThat(match.tournamentName()).isEqualTo("Mid Wars");
            assertThat(match.playedAt()).isEqualTo(PLAYED_AT);
            assertThat(match.teamAName()).isEqualTo("Radiant Ops");
            assertThat(match.teamBName()).isEqualTo("Dire Ops");
            assertThat(match.winnerTeamId()).isEqualTo(TEAM_ID);
        });
    }

    @Test
    void organizerAnalyticsReturnOwnedTournamentCounts() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.ORGANIZER));
        when(roleBasedAnalyticsRepository.findOrganizerCounts(eq(PROFILE_ID), eq(false), any(AnalyticsFilters.class)))
                .thenReturn(new RoleBasedAnalyticsRepository.OrganizerAnalyticsCounts(3, 2, 1, 2, 4, 5));

        var response = service.organizerAnalytics();

        assertThat(response.tournaments()).isEqualTo(3);
        assertThat(response.pendingRegistrations()).isEqualTo(2);
        assertThat(response.approvedRegistrations()).isEqualTo(1);
        assertThat(response.processedMatchGames()).isEqualTo(4);
    }

    @Test
    void organizerCannotViewAnalyticsForAnotherTournament() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.ORGANIZER));
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament()));
        when(tournamentRepository.canManage(TOURNAMENT_ID, PROFILE_ID, false)).thenReturn(false);

        assertThatThrownBy(() -> service.organizerTournamentAnalytics(TOURNAMENT_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only tournament organizers can view private tournament analytics.");

        verify(roleBasedAnalyticsRepository, never()).findTournamentOperationalMetrics(any());
    }

    @Test
    void organizerTournamentAnalyticsUseRealOperationalCountsAndEmptyMetricLists() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.ORGANIZER));
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament()));
        when(tournamentRepository.canManage(TOURNAMENT_ID, PROFILE_ID, false)).thenReturn(true);
        when(roleBasedAnalyticsRepository.findTournamentOperationalMetrics(eq(TOURNAMENT_ID), any(AnalyticsFilters.class)))
                .thenReturn(new RoleBasedAnalyticsRepository.TournamentOperationalMetrics(
                        4,
                        1,
                        new BigDecimal("80.00"),
                        2400));
        when(roleBasedAnalyticsRepository.findRecentImports(eq(TOURNAMENT_ID), any(AnalyticsFilters.class)))
                .thenReturn(List.of());
        when(analyticsQueryService.protectedTournamentMetrics(any(AnalyticsFilters.class))).thenReturn(Optional.empty());

        var response = service.organizerTournamentAnalytics(TOURNAMENT_ID);

        assertThat(response.gamesProcessed()).isEqualTo(4);
        assertThat(response.matchesWithoutImport()).isOne();
        assertThat(response.importCoveragePercent()).isEqualByComparingTo("80.00");
        assertThat(response.tournamentSummary()).isNull();
        assertThat(response.topTeams()).isEmpty();
        assertThat(response.heroMetrics()).isEmpty();
        assertThat(response.teamComparison()).isEmpty();
        assertThat(response.recentImports()).isEmpty();
    }

    private AuthenticatedActor actor(ProfileRole role) {
        return new AuthenticatedActor(AUTH_USER_ID, PROFILE_ID, "profile@example.test", null, role);
    }

    private AnalyticsMatchHistoryResponse matchHistory() {
        return new AnalyticsMatchHistoryResponse(
                MATCH_ID,
                MATCH_GAME_ID,
                "7894561230",
                TOURNAMENT_ID,
                "Mid Wars",
                PLAYED_AT,
                TEAM_ID,
                "Radiant Ops",
                OPPONENT_TEAM_ID,
                "Dire Ops",
                TEAM_ID);
    }

    private Team team() {
        return new Team(
                TEAM_ID,
                "Radiant Ops",
                "RAD",
                "radiant-ops",
                PROFILE_ID,
                "Captain",
                "EU",
                null,
                null,
                AUTH_USER_ID,
                NOW,
                NOW);
    }

    private si.um.feri.dotaops.backend.tournament.domain.Tournament tournament() {
        return new si.um.feri.dotaops.backend.tournament.domain.Tournament(
                TOURNAMENT_ID,
                "mid-wars",
                "Mid Wars",
                si.um.feri.dotaops.backend.tournament.domain.TournamentStatus.DRAFT,
                si.um.feri.dotaops.backend.tournament.domain.TournamentFormat.SINGLE_ELIMINATION,
                PROFILE_ID,
                "Organizer",
                null,
                null,
                null,
                8,
                NOW.plusDays(1),
                null,
                null,
                null,
                false,
                AUTH_USER_ID,
                "UTC",
                null,
                null,
                null,
                si.um.feri.dotaops.backend.tournament.domain.TournamentSettings.defaults(
                        si.um.feri.dotaops.backend.tournament.domain.TournamentFormat.SINGLE_ELIMINATION,
                        8),
                0,
                NOW,
                NOW);
    }
}
