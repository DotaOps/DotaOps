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
import si.um.feri.dotaops.backend.analytics.domain.ContextWeightClassification;
import si.um.feri.dotaops.backend.analytics.domain.ContextWeightReason;
import si.um.feri.dotaops.backend.analytics.domain.HeroMasteryMetrics;
import si.um.feri.dotaops.backend.analytics.repository.RoleBasedAnalyticsRepository;
import si.um.feri.dotaops.backend.analytics.web.AnalyticsMatchHistoryResponse;
import si.um.feri.dotaops.backend.analytics.web.HeroMasteryComparisonDirection;
import si.um.feri.dotaops.backend.analytics.web.HeroMasteryNoteCategory;
import si.um.feri.dotaops.backend.analytics.web.HeroMasteryVerdict;
import si.um.feri.dotaops.backend.analytics.web.PlayerComparisonMetricResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerHeroPerformanceResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerInsightCategory;
import si.um.feri.dotaops.backend.analytics.web.PlayerInsightResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerProgressPointResponse;
import si.um.feri.dotaops.backend.auth.domain.AuthenticatedActor;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
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
    private static final UUID HERO_ID = UUID.fromString("88888888-8888-4888-8888-888888888888");
    private static final UUID HERO_TWO_ID = UUID.fromString("99999999-9999-4999-8999-999999999999");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-12T00:00:00Z");
    private static final OffsetDateTime PLAYED_AT = OffsetDateTime.parse("2026-05-11T18:30:00Z");

    private final AnalyticsQueryService analyticsQueryService = mock(AnalyticsQueryService.class);
    private final RoleBasedAnalyticsRepository roleBasedAnalyticsRepository = mock(RoleBasedAnalyticsRepository.class);
    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
    private final TournamentRepository tournamentRepository = mock(TournamentRepository.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final AnalyticsContextWeightingService contextWeightingService = new AnalyticsContextWeightingService();
    private final RoleBasedAnalyticsService service = new RoleBasedAnalyticsService(
            analyticsQueryService,
            roleBasedAnalyticsRepository,
            teamRepository,
            teamMemberRepository,
            tournamentRepository,
            currentUserProvider,
            contextWeightingService);

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
    void currentPlayerProgressScopesToCurrentProfileAndReturnsProgressRows() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(analyticsQueryService.playerProgress(eq(PROFILE_ID), any(AnalyticsFilters.class), eq(false)))
                .thenReturn(List.of(progressPoint()));

        var response = service.currentPlayerProgress(new AnalyticsFilters(
                TOURNAMENT_ID,
                TEAM_ID,
                PROFILE_ID,
                HERO_ID,
                OffsetDateTime.parse("2026-05-01T00:00:00Z"),
                OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                25));

        ArgumentCaptor<AnalyticsFilters> filters = ArgumentCaptor.forClass(AnalyticsFilters.class);
        verify(analyticsQueryService).playerProgress(eq(PROFILE_ID), filters.capture(), eq(false));
        assertThat(filters.getValue().tournamentId()).isEqualTo(TOURNAMENT_ID);
        assertThat(filters.getValue().teamId()).isEqualTo(TEAM_ID);
        assertThat(filters.getValue().profileId()).isEqualTo(PROFILE_ID);
        assertThat(filters.getValue().heroId()).isEqualTo(HERO_ID);
        assertThat(filters.getValue().limit()).isEqualTo(25);
        assertThat(response).singleElement().satisfies(point -> {
            assertThat(point.playedAt()).isEqualTo(PLAYED_AT);
            assertThat(point.matchId()).isEqualTo(MATCH_ID);
            assertThat(point.matchGameId()).isEqualTo(MATCH_GAME_ID);
            assertThat(point.dotaMatchId()).isEqualTo("7894561230");
            assertThat(point.heroId()).isEqualTo(HERO_ID);
            assertThat(point.dotaHeroId()).isEqualTo(1);
            assertThat(point.heroName()).isEqualTo("Anti-Mage");
            assertThat(point.kills()).isEqualTo(12);
            assertThat(point.deaths()).isEqualTo(3);
            assertThat(point.assists()).isEqualTo(14);
            assertThat(point.kda()).isEqualByComparingTo("8.67");
            assertThat(point.goldPerMin()).isEqualTo(640);
            assertThat(point.xpPerMin()).isEqualTo(720);
            assertThat(point.heroDamage()).isEqualTo(28000);
            assertThat(point.towerDamage()).isEqualTo(5400);
            assertThat(point.heroHealing()).isEqualTo(0);
            assertThat(point.lastHits()).isEqualTo(320);
            assertThat(point.denies()).isEqualTo(12);
            assertThat(point.won()).isTrue();
            assertThat(point.netWorth()).isEqualTo(21000);
            assertThat(point.level()).isEqualTo(22);
            assertThat(point.durationSeconds()).isEqualTo(2400);
            assertThat(point.teamSide()).isEqualTo("RADIANT");
            assertThat(point.radiantScore()).isEqualTo(38);
            assertThat(point.direScore()).isEqualTo(24);
            assertThat(point.winnerSide()).isEqualTo("RADIANT");
        });
    }

    @Test
    void currentPlayerProgressReturnsEmptyListWhenNoMatchDataExists() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(analyticsQueryService.playerProgress(eq(PROFILE_ID), any(AnalyticsFilters.class), eq(false)))
                .thenReturn(List.of());

        var response = service.currentPlayerProgress(new AnalyticsFilters(null, null, PROFILE_ID, null, 10));

        verify(analyticsQueryService).playerProgress(eq(PROFILE_ID), any(AnalyticsFilters.class), eq(false));
        assertThat(response).isEmpty();
    }

    @Test
    void currentPlayerProgressRejectsAnotherProfileFilter() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));

        assertThatThrownBy(() -> service.currentPlayerProgress(new AnalyticsFilters(
                null,
                null,
                UUID.fromString("99999999-9999-4999-8999-999999999999"),
                null,
                10)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Players can only view their own private analytics.");
    }

    @Test
    void currentPlayerHeroPerformanceScopesToCurrentProfileAndReturnsMultipleHeroes() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(analyticsQueryService.playerHeroPerformance(eq(PROFILE_ID), any(AnalyticsFilters.class), eq(false)))
                .thenReturn(List.of(heroPerformance(), secondHeroPerformance()));

        var response = service.currentPlayerHeroPerformance(new AnalyticsFilters(
                TOURNAMENT_ID,
                TEAM_ID,
                PROFILE_ID,
                null,
                OffsetDateTime.parse("2026-05-01T00:00:00Z"),
                OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                25));

        ArgumentCaptor<AnalyticsFilters> filters = ArgumentCaptor.forClass(AnalyticsFilters.class);
        verify(analyticsQueryService).playerHeroPerformance(eq(PROFILE_ID), filters.capture(), eq(false));
        assertThat(filters.getValue().tournamentId()).isEqualTo(TOURNAMENT_ID);
        assertThat(filters.getValue().teamId()).isEqualTo(TEAM_ID);
        assertThat(filters.getValue().profileId()).isEqualTo(PROFILE_ID);
        assertThat(filters.getValue().limit()).isEqualTo(25);
        assertThat(response).hasSize(2);
        assertThat(response.getFirst()).satisfies(hero -> {
            assertThat(hero.heroId()).isEqualTo(HERO_ID);
            assertThat(hero.heroName()).isEqualTo("Anti-Mage");
            assertThat(hero.matches()).isEqualTo(2);
            assertThat(hero.wins()).isEqualTo(1);
            assertThat(hero.losses()).isEqualTo(1);
            assertThat(hero.winRate()).isEqualByComparingTo("50.00");
            assertThat(hero.avgKills()).isEqualByComparingTo("9.50");
            assertThat(hero.avgDeaths()).isEqualByComparingTo("3.50");
            assertThat(hero.avgAssists()).isEqualByComparingTo("12.00");
            assertThat(hero.avgKda()).isEqualByComparingTo("6.14");
            assertThat(hero.avgGpm()).isEqualByComparingTo("610.00");
            assertThat(hero.avgXpm()).isEqualByComparingTo("690.00");
            assertThat(hero.avgHeroDamage()).isEqualByComparingTo("24500.00");
            assertThat(hero.avgTowerDamage()).isEqualByComparingTo("3900.00");
            assertThat(hero.avgHeroHealing()).isEqualByComparingTo("0.00");
            assertThat(hero.avgLastHits()).isEqualByComparingTo("285.00");
            assertThat(hero.avgDenies()).isEqualByComparingTo("10.50");
            assertThat(hero.recentMatchId()).isEqualTo(MATCH_ID);
            assertThat(hero.recentMatchGameId()).isEqualTo(MATCH_GAME_ID);
            assertThat(hero.recentDotaMatchId()).isEqualTo("7894561230");
            assertThat(hero.recentPlayedAt()).isEqualTo(PLAYED_AT);
            assertThat(hero.bestMatchId()).isEqualTo(MATCH_ID);
            assertThat(hero.bestKda()).isEqualByComparingTo("8.67");
        });
        assertThat(response.get(1).heroId()).isEqualTo(HERO_TWO_ID);
        assertThat(response.get(1).matches()).isOne();
    }

    @Test
    void currentPlayerHeroPerformanceReturnsEmptyListWhenNoDataExists() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(analyticsQueryService.playerHeroPerformance(eq(PROFILE_ID), any(AnalyticsFilters.class), eq(false)))
                .thenReturn(List.of());

        var response = service.currentPlayerHeroPerformance(new AnalyticsFilters(null, null, PROFILE_ID, null, 10));

        verify(analyticsQueryService).playerHeroPerformance(eq(PROFILE_ID), any(AnalyticsFilters.class), eq(false));
        assertThat(response).isEmpty();
    }

    @Test
    void currentPlayerHeroPerformanceRejectsAnotherProfileFilter() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));

        assertThatThrownBy(() -> service.currentPlayerHeroPerformance(new AnalyticsFilters(
                null,
                null,
                UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
                null,
                10)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Players can only view their own private analytics.");
    }

    @Test
    void currentPlayerHeroMasteryReturnsStrongVerdictWithBaselineComparisonAndRawMetrics() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(analyticsQueryService.playerHeroMasteryMetrics(eq(PROFILE_ID), eq(HERO_ID), any(), eq(false)))
                .thenReturn(Optional.of(heroMasteryMetrics(
                        5,
                        4,
                        1,
                        "80.00",
                        "8.00",
                        "2.00",
                        "650.00",
                        "720.00",
                        "300.00",
                        "14.00",
                        "22000.00",
                        "26000.00",
                        "5200.00",
                        "200.00",
                        "23.00")));
        when(analyticsQueryService.playerComparisonHeadlineMetrics(eq(PROFILE_ID), any(), eq(false)))
                .thenReturn(Optional.of(overallBaseline()));
        when(analyticsQueryService.playerProgress(eq(PROFILE_ID), any(), eq(false)))
                .thenReturn(List.of(
                        progressPoint(NOW.minusDays(5), "8.00", 2, 650, 720),
                        progressPoint(NOW.minusDays(4), "8.00", 2, 660, 730),
                        progressPoint(NOW.minusDays(3), "8.00", 2, 640, 710),
                        progressPoint(NOW.minusDays(2), "8.00", 2, 650, 720),
                        progressPoint(NOW.minusDays(1), "8.00", 2, 650, 720)));

        var response = service.currentPlayerHeroMastery(HERO_ID, new AnalyticsFilters(
                TOURNAMENT_ID,
                TEAM_ID,
                PROFILE_ID,
                null,
                10));

        ArgumentCaptor<AnalyticsFilters> masteryFilters = ArgumentCaptor.forClass(AnalyticsFilters.class);
        verify(analyticsQueryService).playerHeroMasteryMetrics(eq(PROFILE_ID), eq(HERO_ID), masteryFilters.capture(), eq(false));
        assertThat(masteryFilters.getValue().heroId()).isEqualTo(HERO_ID);
        assertThat(masteryFilters.getValue().profileId()).isEqualTo(PROFILE_ID);
        assertThat(response.masteryVerdict()).isEqualTo(HeroMasteryVerdict.STRONG);
        assertThat(response.games()).isEqualTo(5);
        assertThat(response.winRate()).isEqualByComparingTo("80.00");
        assertThat(response.avgDeaths()).isEqualByComparingTo("2.00");
        assertThat(response.avgNetWorth()).isEqualByComparingTo("22000.00");
        assertThat(response.avgLevel()).isEqualByComparingTo("23.00");
        assertThat(response.comparisonToPlayerOverallBaseline())
                .anySatisfy(comparison -> {
                    assertThat(comparison.metric()).isEqualTo("winRate");
                    assertThat(comparison.direction()).isEqualTo(HeroMasteryComparisonDirection.BETTER);
                    assertThat(comparison.heroValue()).isEqualByComparingTo("80.00");
                    assertThat(comparison.overallValue()).isEqualByComparingTo("55.00");
                });
        assertThat(response.contextSummary().normalGameCount()).isEqualTo(5);
        assertThat(response.recentMatches()).hasSize(5);
    }

    @Test
    void currentPlayerHeroMasteryReturnsInsufficientDataWhenHeroSampleIsTooSmall() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(analyticsQueryService.playerHeroMasteryMetrics(eq(PROFILE_ID), eq(HERO_ID), any(), eq(false)))
                .thenReturn(Optional.of(heroMasteryMetrics(
                        2,
                        1,
                        1,
                        "50.00",
                        "3.00",
                        "4.00",
                        "520.00",
                        "580.00",
                        "210.00",
                        "8.00",
                        "18000.00",
                        "18000.00",
                        "2200.00",
                        "0.00",
                        "18.00")));
        when(analyticsQueryService.playerComparisonHeadlineMetrics(eq(PROFILE_ID), any(), eq(false)))
                .thenReturn(Optional.of(overallBaseline()));
        when(analyticsQueryService.playerProgress(eq(PROFILE_ID), any(), eq(false)))
                .thenReturn(List.of(progressPoint(NOW.minusDays(2), "3.00", 4, 520, 580)));

        var response = service.currentPlayerHeroMastery(HERO_ID, new AnalyticsFilters(null, null, PROFILE_ID, null, 10));

        assertThat(response.masteryVerdict()).isEqualTo(HeroMasteryVerdict.INSUFFICIENT_DATA);
        assertThat(response.deterministicNotes())
                .anySatisfy(note -> {
                    assertThat(note.category()).isEqualTo(HeroMasteryNoteCategory.SAMPLE_SIZE);
                    assertThat(note.message()).contains("Not enough matches");
                });
    }

    @Test
    void currentPlayerHeroMasteryReturnsNeedsWorkWhenMultipleImportantMetricsTrailBaseline() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(analyticsQueryService.playerHeroMasteryMetrics(eq(PROFILE_ID), eq(HERO_ID), any(), eq(false)))
                .thenReturn(Optional.of(heroMasteryMetrics(
                        5,
                        1,
                        4,
                        "20.00",
                        "1.40",
                        "8.00",
                        "410.00",
                        "450.00",
                        "140.00",
                        "3.00",
                        "9800.00",
                        "9000.00",
                        "300.00",
                        "0.00",
                        "12.00")));
        when(analyticsQueryService.playerComparisonHeadlineMetrics(eq(PROFILE_ID), any(), eq(false)))
                .thenReturn(Optional.of(overallBaseline()));
        when(analyticsQueryService.playerProgress(eq(PROFILE_ID), any(), eq(false)))
                .thenReturn(List.of(
                        progressPoint(NOW.minusDays(5), "1.40", 8, 410, 450),
                        progressPoint(NOW.minusDays(4), "1.40", 8, 410, 450),
                        progressPoint(NOW.minusDays(3), "1.40", 8, 410, 450),
                        progressPoint(NOW.minusDays(2), "1.40", 8, 410, 450),
                        progressPoint(NOW.minusDays(1), "1.40", 8, 410, 450)));

        var response = service.currentPlayerHeroMastery(HERO_ID, new AnalyticsFilters(null, null, PROFILE_ID, null, 10));

        assertThat(response.masteryVerdict()).isEqualTo(HeroMasteryVerdict.NEEDS_WORK);
        assertThat(response.comparisonToPlayerOverallBaseline())
                .anySatisfy(comparison -> {
                    assertThat(comparison.metric()).isEqualTo("deaths");
                    assertThat(comparison.direction()).isEqualTo(HeroMasteryComparisonDirection.WORSE);
                });
        assertThat(response.deterministicNotes())
                .anySatisfy(note -> {
                    assertThat(note.category()).isEqualTo(HeroMasteryNoteCategory.SURVIVABILITY);
                    assertThat(note.message()).contains("die more often");
                });
    }

    @Test
    void currentPlayerHeroMasteryContextSummaryDetectsRoughAndStompGamesWithoutChangingRawMetrics() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(analyticsQueryService.playerHeroMasteryMetrics(eq(PROFILE_ID), eq(HERO_ID), any(), eq(false)))
                .thenReturn(Optional.of(heroMasteryMetrics(
                        3,
                        1,
                        2,
                        "33.33",
                        "2.00",
                        "12.00",
                        "300.00",
                        "330.00",
                        "120.00",
                        "2.00",
                        "7200.00",
                        "4500.00",
                        "100.00",
                        "0.00",
                        "10.00")));
        when(analyticsQueryService.playerComparisonHeadlineMetrics(eq(PROFILE_ID), any(), eq(false)))
                .thenReturn(Optional.of(overallBaseline()));
        when(analyticsQueryService.playerProgress(eq(PROFILE_ID), any(), eq(false)))
                .thenReturn(List.of(
                        progressPoint(NOW.minusDays(3), "3.00", 4, 520, 580),
                        progressPoint(NOW.minusDays(2), "1.20", 8, 420, 450),
                        progressPoint(
                                NOW.minusDays(1),
                                "0.40",
                                2,
                                16,
                                2,
                                220,
                                280,
                                3000,
                                0,
                                0,
                                false)));

        var response = service.currentPlayerHeroMastery(HERO_ID, new AnalyticsFilters(null, null, PROFILE_ID, null, 10));

        assertThat(response.avgDeaths()).isEqualByComparingTo("12.00");
        assertThat(response.avgHeroDamage()).isEqualByComparingTo("4500.00");
        assertThat(response.contextSummary().roughGameCount()).isGreaterThanOrEqualTo(1);
        assertThat(response.contextSummary().stompLossCount()).isOne();
        assertThat(response.contextSummary().averageContextWeight()).isLessThan(BigDecimal.ONE);
        assertThat(response.recentMatches())
                .anySatisfy(match -> {
                    assertThat(match.contextClassification()).isEqualTo(ContextWeightClassification.STOMP_LOSS);
                    assertThat(match.contextReasons()).contains(ContextWeightReason.STOMP_LOSS_CONTEXT);
                    assertThat(match.deaths()).isEqualTo(16);
                });
        assertThat(response.deterministicNotes())
                .anySatisfy(note -> assertThat(note.category()).isEqualTo(HeroMasteryNoteCategory.CONTEXT));
    }

    @Test
    void currentPlayerHeroMasteryReturnsStableEmptyStateWhenPlayerHasNoAnalyticsData() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(analyticsQueryService.playerHeroMasteryMetrics(eq(PROFILE_ID), eq(HERO_ID), any(), eq(false)))
                .thenReturn(Optional.empty());
        when(analyticsQueryService.playerComparisonHeadlineMetrics(eq(PROFILE_ID), any(), eq(false)))
                .thenReturn(Optional.empty());
        when(analyticsQueryService.playerProgress(eq(PROFILE_ID), any(), eq(false))).thenReturn(List.of());

        var response = service.currentPlayerHeroMastery(HERO_ID, new AnalyticsFilters(null, null, PROFILE_ID, null, 10));

        assertThat(response.profileId()).isEqualTo(PROFILE_ID);
        assertThat(response.heroId()).isEqualTo(HERO_ID);
        assertThat(response.games()).isZero();
        assertThat(response.masteryVerdict()).isEqualTo(HeroMasteryVerdict.INSUFFICIENT_DATA);
        assertThat(response.recentMatches()).isEmpty();
        assertThat(response.comparisonToPlayerOverallBaseline()).isEmpty();
        assertThat(response.contextSummary().averageContextWeight()).isEqualByComparingTo("1.00");
    }

    @Test
    void currentPlayerHeroMasteryReturnsInsufficientDataWhenHeroHasNoMatchesButOverallBaselineExists() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(analyticsQueryService.playerHeroMasteryMetrics(eq(PROFILE_ID), eq(HERO_ID), any(), eq(false)))
                .thenReturn(Optional.empty());
        when(analyticsQueryService.playerComparisonHeadlineMetrics(eq(PROFILE_ID), any(), eq(false)))
                .thenReturn(Optional.of(overallBaseline()));
        when(analyticsQueryService.playerProgress(eq(PROFILE_ID), any(), eq(false))).thenReturn(List.of());

        var response = service.currentPlayerHeroMastery(HERO_ID, new AnalyticsFilters(null, null, PROFILE_ID, null, 10));

        assertThat(response.games()).isZero();
        assertThat(response.masteryVerdict()).isEqualTo(HeroMasteryVerdict.INSUFFICIENT_DATA);
        assertThat(response.deterministicNotes())
                .anySatisfy(note -> assertThat(note.category()).isEqualTo(HeroMasteryNoteCategory.SAMPLE_SIZE));
    }

    @Test
    void currentPlayerHeroMasteryRejectsMismatchedHeroQueryFilter() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));

        assertThatThrownBy(() -> service.currentPlayerHeroMastery(
                HERO_ID,
                new AnalyticsFilters(null, null, PROFILE_ID, HERO_TWO_ID, 10)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Hero filter does not match the route hero.");
    }

    @Test
    void currentPlayerInsightsReturnPositiveKdaTrendWhenRecentMatchesImprove() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(analyticsQueryService.playerProgress(eq(PROFILE_ID), any(AnalyticsFilters.class), eq(false)))
                .thenReturn(List.of(
                        progressPoint(NOW.minusDays(6), "2.00", 3, 520, 580),
                        progressPoint(NOW.minusDays(5), "2.20", 3, 520, 580),
                        progressPoint(NOW.minusDays(4), "2.10", 3, 520, 580),
                        progressPoint(NOW.minusDays(3), "4.00", 3, 520, 580),
                        progressPoint(NOW.minusDays(2), "4.20", 3, 520, 580),
                        progressPoint(NOW.minusDays(1), "4.10", 3, 520, 580)));
        when(analyticsQueryService.playerHeroPerformance(eq(PROFILE_ID), any(AnalyticsFilters.class), eq(false)))
                .thenReturn(List.of());

        var response = service.currentPlayerInsights(new AnalyticsFilters(null, null, PROFILE_ID, null, 10));

        ArgumentCaptor<AnalyticsFilters> filters = ArgumentCaptor.forClass(AnalyticsFilters.class);
        verify(analyticsQueryService).playerProgress(eq(PROFILE_ID), filters.capture(), eq(false));
        assertThat(filters.getValue().profileId()).isEqualTo(PROFILE_ID);
        assertThat(filters.getValue().limit()).isEqualTo(10);
        PlayerInsightResponse insight = response.stream()
                .filter(item -> item.metricName().equals("KDA"))
                .findFirst()
                .orElseThrow();
        assertThat(insight.category()).isEqualTo(PlayerInsightCategory.POSITIVE);
        assertThat(insight.title()).isEqualTo("KDA trend is improving");
        assertThat(insight.currentValue()).isEqualByComparingTo("4.10");
        assertThat(insight.comparisonValue()).isEqualByComparingTo("2.10");
        assertThat(insight.sampleSize()).isEqualTo(6);
        assertThat(insight.evidence()).contains("3 recent matches");
    }

    @Test
    void currentPlayerInsightsReturnContextWeightInsightForRoughGames() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(analyticsQueryService.playerProgress(eq(PROFILE_ID), any(AnalyticsFilters.class), eq(false)))
                .thenReturn(List.of(
                        progressPoint(NOW.minusDays(6), "3.00", 3, 520, 580),
                        progressPoint(NOW.minusDays(5), "3.00", 3, 520, 580),
                        progressPoint(NOW.minusDays(4), "3.00", 3, 520, 580),
                        progressPoint(NOW.minusDays(3), "3.00", 3, 520, 580),
                        progressPoint(NOW.minusDays(2), "3.00", 3, 520, 580),
                        progressPoint(
                                NOW.minusDays(1),
                                "0.40",
                                2,
                                16,
                                2,
                                220,
                                280,
                                3000,
                                0,
                                0,
                                false)));
        when(analyticsQueryService.playerHeroPerformance(eq(PROFILE_ID), any(AnalyticsFilters.class), eq(false)))
                .thenReturn(List.of());

        var response = service.currentPlayerInsights(new AnalyticsFilters(null, null, PROFILE_ID, null, 10));

        PlayerInsightResponse insight = response.stream()
                .filter(item -> item.metricName().equals("contextWeight"))
                .findFirst()
                .orElseThrow();
        assertThat(insight.category()).isEqualTo(PlayerInsightCategory.INFO);
        assertThat(insight.currentValue()).isEqualByComparingTo("0.35");
        assertThat(insight.comparisonValue()).isEqualByComparingTo("1.00");
        assertThat(insight.contextWeight()).isNotNull();
        assertThat(insight.contextWeight().classification()).isEqualTo(ContextWeightClassification.STOMP_LOSS);
        assertThat(insight.contextWeight().reasons())
                .contains(ContextWeightReason.HIGH_DEATHS, ContextWeightReason.LOW_KDA);
        assertThat(insight.description()).contains("Raw match values stay unchanged");
    }

    @Test
    void currentPlayerInsightsReturnWarningWhenDeathsAreHighOnHero() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(analyticsQueryService.playerProgress(eq(PROFILE_ID), any(AnalyticsFilters.class), eq(false)))
                .thenReturn(List.of(
                        progressPoint(NOW.minusDays(6), "3.00", 3, 520, 580),
                        progressPoint(NOW.minusDays(5), "3.00", 3, 520, 580),
                        progressPoint(NOW.minusDays(4), "3.00", 3, 520, 580),
                        progressPoint(NOW.minusDays(3), "3.00", 3, 520, 580),
                        progressPoint(NOW.minusDays(2), "3.00", 3, 520, 580),
                        progressPoint(NOW.minusDays(1), "3.00", 3, 520, 580)));
        when(analyticsQueryService.playerHeroPerformance(eq(PROFILE_ID), any(AnalyticsFilters.class), eq(false)))
                .thenReturn(List.of(heroPerformance(HERO_ID, "Anti-Mage", 3, "50.00", "5.00", "3.00")));

        var response = service.currentPlayerInsights(new AnalyticsFilters(null, null, PROFILE_ID, null, 10));

        PlayerInsightResponse insight = response.stream()
                .filter(item -> item.metricName().equals("avgDeaths"))
                .findFirst()
                .orElseThrow();
        assertThat(insight.category()).isEqualTo(PlayerInsightCategory.WARNING);
        assertThat(insight.title()).isEqualTo("Deaths are high on Anti-Mage");
        assertThat(insight.currentValue()).isEqualByComparingTo("5.00");
        assertThat(insight.comparisonValue()).isEqualByComparingTo("3.00");
        assertThat(insight.sampleSize()).isEqualTo(3);
        assertThat(insight.description()).contains("Anti-Mage");
    }

    @Test
    void currentPlayerInsightsReturnEmptyListWhenDataIsTooSparse() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(analyticsQueryService.playerProgress(eq(PROFILE_ID), any(AnalyticsFilters.class), eq(false)))
                .thenReturn(List.of(
                        progressPoint(NOW.minusDays(2), "3.00", 3, 520, 580),
                        progressPoint(NOW.minusDays(1), "3.20", 3, 520, 580)));
        when(analyticsQueryService.playerHeroPerformance(eq(PROFILE_ID), any(AnalyticsFilters.class), eq(false)))
                .thenReturn(List.of(heroPerformance(HERO_ID, "Anti-Mage", 2, "100.00", "6.00", "7.00")));

        var response = service.currentPlayerInsights(new AnalyticsFilters(null, null, PROFILE_ID, null, 10));

        verify(analyticsQueryService).playerProgress(eq(PROFILE_ID), any(AnalyticsFilters.class), eq(false));
        verify(analyticsQueryService).playerHeroPerformance(eq(PROFILE_ID), any(AnalyticsFilters.class), eq(false));
        assertThat(response).isEmpty();
    }

    @Test
    void currentPlayerInsightsReturnHeroSpecificInsightWhenMinimumSampleExists() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(analyticsQueryService.playerProgress(eq(PROFILE_ID), any(AnalyticsFilters.class), eq(false)))
                .thenReturn(List.of(
                        progressPoint(NOW.minusDays(6), "3.00", 3, 520, 580),
                        progressPoint(NOW.minusDays(5), "3.00", 3, 520, 580),
                        progressPoint(NOW.minusDays(4), "3.00", 3, 520, 580),
                        progressPoint(NOW.minusDays(3), "3.00", 3, 520, 580),
                        progressPoint(NOW.minusDays(2), "3.00", 3, 520, 580),
                        progressPoint(NOW.minusDays(1), "3.00", 3, 520, 580)));
        when(analyticsQueryService.playerHeroPerformance(eq(PROFILE_ID), any(AnalyticsFilters.class), eq(false)))
                .thenReturn(List.of(heroPerformance(HERO_ID, "Anti-Mage", 3, "60.00", "3.00", "5.00")));

        var response = service.currentPlayerInsights(new AnalyticsFilters(null, null, PROFILE_ID, null, 10));

        PlayerInsightResponse insight = response.stream()
                .filter(item -> item.metricName().equals("avgKda"))
                .findFirst()
                .orElseThrow();
        assertThat(insight.category()).isEqualTo(PlayerInsightCategory.POSITIVE);
        assertThat(insight.title()).isEqualTo("You perform better with Anti-Mage");
        assertThat(insight.currentValue()).isEqualByComparingTo("5.00");
        assertThat(insight.comparisonValue()).isEqualByComparingTo("3.00");
        assertThat(insight.sampleSize()).isEqualTo(3);
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

    private PlayerProgressPointResponse progressPoint() {
        return new PlayerProgressPointResponse(
                PLAYED_AT,
                MATCH_ID,
                MATCH_GAME_ID,
                "7894561230",
                HERO_ID,
                1,
                "Anti-Mage",
                12,
                3,
                14,
                new BigDecimal("8.67"),
                640,
                720,
                28000,
                5400,
                0,
                320,
                12,
                true,
                21000,
                22,
                2400,
                "RADIANT",
                38,
                24,
                "RADIANT");
    }

    private HeroMasteryMetrics heroMasteryMetrics(
            int games,
            int wins,
            int losses,
            String winRate,
            String kda,
            String avgDeaths,
            String avgGoldPerMin,
            String avgXpPerMin,
            String avgLastHits,
            String avgDenies,
            String avgNetWorth,
            String avgHeroDamage,
            String avgTowerDamage,
            String avgHeroHealing,
            String avgLevel
    ) {
        return new HeroMasteryMetrics(
                PROFILE_ID,
                HERO_ID,
                "Anti-Mage",
                games,
                wins,
                losses,
                new BigDecimal(winRate),
                new BigDecimal("8.00"),
                new BigDecimal(avgDeaths),
                new BigDecimal("10.00"),
                new BigDecimal(kda),
                new BigDecimal(avgGoldPerMin),
                new BigDecimal(avgXpPerMin),
                new BigDecimal(avgLastHits),
                new BigDecimal(avgDenies),
                new BigDecimal(avgNetWorth),
                new BigDecimal(avgHeroDamage),
                new BigDecimal(avgTowerDamage),
                new BigDecimal(avgHeroHealing),
                new BigDecimal(avgLevel));
    }

    private PlayerComparisonMetricResponse overallBaseline() {
        return new PlayerComparisonMetricResponse(
                PROFILE_ID,
                "Carry Player",
                10,
                6,
                4,
                new BigDecimal("55.00"),
                new BigDecimal("4.00"),
                new BigDecimal("6.00"),
                new BigDecimal("4.00"),
                new BigDecimal("10.00"),
                new BigDecimal("560.00"),
                new BigDecimal("620.00"),
                new BigDecimal("230.00"),
                new BigDecimal("8.00"),
                new BigDecimal("17500.00"),
                new BigDecimal("18000.00"),
                new BigDecimal("1800.00"),
                new BigDecimal("100.00"));
    }

    private PlayerProgressPointResponse progressPoint(
            OffsetDateTime playedAt,
            String kda,
            int deaths,
            Integer goldPerMin,
            Integer xpPerMin
    ) {
        return new PlayerProgressPointResponse(
                playedAt,
                MATCH_ID,
                MATCH_GAME_ID,
                "7894561230",
                HERO_ID,
                1,
                "Anti-Mage",
                8,
                deaths,
                10,
                new BigDecimal(kda),
                goldPerMin,
                xpPerMin,
                18000,
                2200,
                0,
                210,
                8,
                true,
                18000,
                18,
                2400,
                "RADIANT",
                38,
                24,
                "RADIANT");
    }

    private PlayerProgressPointResponse progressPoint(
            OffsetDateTime playedAt,
            String kda,
            int kills,
            int deaths,
            int assists,
            Integer goldPerMin,
            Integer xpPerMin,
            Integer heroDamage,
            Integer towerDamage,
            Integer heroHealing,
            Boolean won
    ) {
        return new PlayerProgressPointResponse(
                playedAt,
                MATCH_ID,
                MATCH_GAME_ID,
                "7894561230",
                HERO_ID,
                1,
                "Anti-Mage",
                kills,
                deaths,
                assists,
                new BigDecimal(kda),
                goldPerMin,
                xpPerMin,
                heroDamage,
                towerDamage,
                heroHealing,
                210,
                8,
                won,
                Boolean.FALSE.equals(won) ? 5200 : 18000,
                Boolean.FALSE.equals(won) ? 10 : 18,
                1900,
                "RADIANT",
                Boolean.FALSE.equals(won) ? 8 : 38,
                Boolean.FALSE.equals(won) ? 42 : 24,
                Boolean.FALSE.equals(won) ? "DIRE" : "RADIANT");
    }

    private PlayerHeroPerformanceResponse heroPerformance() {
        return new PlayerHeroPerformanceResponse(
                HERO_ID,
                1,
                "Anti-Mage",
                2,
                1,
                1,
                new BigDecimal("50.00"),
                new BigDecimal("9.50"),
                new BigDecimal("3.50"),
                new BigDecimal("12.00"),
                new BigDecimal("6.14"),
                new BigDecimal("610.00"),
                new BigDecimal("690.00"),
                new BigDecimal("24500.00"),
                new BigDecimal("3900.00"),
                new BigDecimal("0.00"),
                new BigDecimal("285.00"),
                new BigDecimal("10.50"),
                MATCH_ID,
                MATCH_GAME_ID,
                "7894561230",
                PLAYED_AT,
                MATCH_ID,
                MATCH_GAME_ID,
                "7894561230",
                PLAYED_AT,
                new BigDecimal("8.67"));
    }

    private PlayerHeroPerformanceResponse secondHeroPerformance() {
        return new PlayerHeroPerformanceResponse(
                HERO_TWO_ID,
                2,
                "Axe",
                1,
                1,
                0,
                new BigDecimal("100.00"),
                new BigDecimal("6.00"),
                new BigDecimal("2.00"),
                new BigDecimal("18.00"),
                new BigDecimal("12.00"),
                new BigDecimal("430.00"),
                new BigDecimal("510.00"),
                new BigDecimal("18000.00"),
                new BigDecimal("1200.00"),
                new BigDecimal("650.00"),
                new BigDecimal("165.00"),
                new BigDecimal("5.00"),
                MATCH_ID,
                MATCH_GAME_ID,
                "7894561230",
                PLAYED_AT,
                MATCH_ID,
                MATCH_GAME_ID,
                "7894561230",
                PLAYED_AT,
                new BigDecimal("12.00"));
    }

    private PlayerHeroPerformanceResponse heroPerformance(
            UUID heroId,
            String heroName,
            int matches,
            String winRate,
            String avgDeaths,
            String avgKda
    ) {
        return new PlayerHeroPerformanceResponse(
                heroId,
                1,
                heroName,
                matches,
                Math.max(matches - 1, 0),
                matches > 0 ? 1 : 0,
                new BigDecimal(winRate),
                new BigDecimal("7.00"),
                new BigDecimal(avgDeaths),
                new BigDecimal("9.00"),
                new BigDecimal(avgKda),
                new BigDecimal("520.00"),
                new BigDecimal("580.00"),
                new BigDecimal("18000.00"),
                new BigDecimal("2200.00"),
                new BigDecimal("0.00"),
                new BigDecimal("210.00"),
                new BigDecimal("8.00"),
                MATCH_ID,
                MATCH_GAME_ID,
                "7894561230",
                PLAYED_AT,
                MATCH_ID,
                MATCH_GAME_ID,
                "7894561230",
                PLAYED_AT,
                new BigDecimal(avgKda));
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
