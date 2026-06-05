package si.um.feri.dotaops.backend.analytics.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import si.um.feri.dotaops.backend.analytics.domain.AnalyticsFilters;
import si.um.feri.dotaops.backend.analytics.repository.AnalyticsLookupRepository;
import si.um.feri.dotaops.backend.analytics.web.HeroMetricsResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerComparisonMatchPlayerResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerComparisonMatchResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerComparisonMetricResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerHeroPerformanceResponse;
import si.um.feri.dotaops.backend.auth.domain.AuthenticatedActor;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.tournament.repository.TournamentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsComparisonServiceTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CURRENT_PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID OTHER_PROFILE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID THIRD_PROFILE_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID TEAM_A_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID TEAM_B_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID TOURNAMENT_ID = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID HERO_ID = UUID.fromString("88888888-8888-4888-8888-888888888888");
    private static final UUID MATCH_ID = UUID.fromString("99999999-9999-4999-8999-999999999999");

    private final AnalyticsQueryService analyticsQueryService = mock(AnalyticsQueryService.class);
    private final AnalyticsLookupRepository lookupRepository = mock(AnalyticsLookupRepository.class);
    private final TournamentRepository tournamentRepository = mock(TournamentRepository.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final AnalyticsComparisonService service = new AnalyticsComparisonService(
            analyticsQueryService,
            lookupRepository,
            tournamentRepository,
            currentUserProvider);

    @Test
    void playerCanCompareSelfWithAnotherPlayerUsingPublicScopeWhenNotSameTeam() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(lookupRepository.teamsShareActiveMembership(CURRENT_PROFILE_ID, CURRENT_PROFILE_ID, OTHER_PROFILE_ID))
                .thenReturn(false);
        when(analyticsQueryService.sharedHeroesForPlayers(eq(CURRENT_PROFILE_ID), eq(OTHER_PROFILE_ID), any(), eq(true)))
                .thenReturn(List.of());
        when(analyticsQueryService.recentMatchesForPlayers(eq(CURRENT_PROFILE_ID), eq(OTHER_PROFILE_ID), any(), eq(true)))
                .thenReturn(List.of());

        var response = service.comparePlayers(
                CURRENT_PROFILE_ID,
                OTHER_PROFILE_ID,
                new AnalyticsFilters(null, null, null, null, 10));

        assertThat(response.filters().accessScope()).isEqualTo("public");
        verify(analyticsQueryService).playerAggregateMetrics(eq(CURRENT_PROFILE_ID), any(), eq(true));
        verify(analyticsQueryService).playerAggregateMetrics(eq(OTHER_PROFILE_ID), any(), eq(true));
        verify(analyticsQueryService, times(2)).heroMetrics(any());
    }

    @Test
    void playerComparisonIncludesHeadlineSharedHeroDetailsEnrichedMatchesAndWarnings() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.ADMIN));
        when(analyticsQueryService.playerComparisonHeadlineMetrics(eq(CURRENT_PROFILE_ID), any(), eq(false)))
                .thenReturn(Optional.of(comparisonMetric(CURRENT_PROFILE_ID, "Carry One", 8, "620.00", "5.50")));
        when(analyticsQueryService.playerComparisonHeadlineMetrics(eq(OTHER_PROFILE_ID), any(), eq(false)))
                .thenReturn(Optional.of(comparisonMetric(OTHER_PROFILE_ID, "Carry Two", 7, "580.00", "4.25")));
        when(analyticsQueryService.playerHeroPerformance(eq(CURRENT_PROFILE_ID), any(), eq(false)))
                .thenReturn(List.of(heroDetail(4, "5.20", "610.00", "720.00", "22000.00", "2400.00", "2.50")));
        when(analyticsQueryService.playerHeroPerformance(eq(OTHER_PROFILE_ID), any(), eq(false)))
                .thenReturn(List.of(heroDetail(2, "4.10", "560.00", "690.00", "19000.00", "1800.00", "3.00")));
        when(analyticsQueryService.playerComparisonMatches(eq(CURRENT_PROFILE_ID), eq(OTHER_PROFILE_ID), any(), eq(false)))
                .thenReturn(List.of(comparisonMatch()));

        var response = service.comparePlayers(
                CURRENT_PROFILE_ID,
                OTHER_PROFILE_ID,
                new AnalyticsFilters(null, null, null, null, 10));

        assertThat(response.headlineComparison().profileA().avgGpm()).isEqualByComparingTo("620.00");
        assertThat(response.headlineComparison().delta().avgGpm()).isEqualByComparingTo("40.00");
        assertThat(response.headlineComparison().delta().kda()).isEqualByComparingTo("1.25");
        assertThat(response.profileAHeroDetails()).hasSize(1);
        assertThat(response.profileBHeroDetails()).hasSize(1);
        assertThat(response.sharedHeroComparisons()).hasSize(1);
        assertThat(response.sharedHeroComparisons().getFirst().heroName()).isEqualTo("Anti-Mage");
        assertThat(response.sharedHeroComparisons().getFirst().profileA().gamesPlayed()).isEqualTo(4);
        assertThat(response.sharedHeroComparisons().getFirst().delta().avgTowerDamage()).isEqualByComparingTo("600.00");
        assertThat(response.enrichedMatchHistory()).hasSize(1);
        assertThat(response.enrichedMatchHistory().getFirst().profileA().heroName()).isEqualTo("Anti-Mage");
        assertThat(response.enrichedMatchHistory().getFirst().profileA().netWorth()).isEqualTo(21400);
        assertThat(response.warnings())
                .extracting("code")
                .contains("LOW_SHARED_HERO_SAMPLE");
    }

    @Test
    void playerCannotCompareUnrelatedPlayers() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));

        assertThatThrownBy(() -> service.comparePlayers(
                OTHER_PROFILE_ID,
                THIRD_PROFILE_ID,
                new AnalyticsFilters(null, null, null, null, 10)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You cannot compare these players.");
    }

    @Test
    void playerCandidateLookupReturnsExactNameMatch() {
        var candidate = playerCandidate(OTHER_PROFILE_ID, "Aegis Ace", "aegis_ace", 12, 123456789L);
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(lookupRepository.findAnalyzedPlayerComparisonCandidates(
                eq(CURRENT_PROFILE_ID),
                eq("Aegis Ace"),
                any(),
                eq(true),
                eq(true),
                eq(10)))
                .thenReturn(List.of(candidate));

        var response = service.playerComparisonCandidates(
                "  Aegis   Ace ",
                new AnalyticsFilters(null, null, null, null, 10));

        assertThat(response.query()).isEqualTo("Aegis Ace");
        assertThat(response.exactMatch()).isTrue();
        assertThat(response.ambiguous()).isFalse();
        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().getFirst().profileId()).isEqualTo(OTHER_PROFILE_ID);
        assertThat(response.candidates().getFirst().displayName()).isEqualTo("Aegis Ace");
        assertThat(response.candidates().getFirst().opendotaAccountId()).isEqualTo(123456789L);
        assertThat(response.candidates().getFirst().analyticsGamesCount()).isEqualTo(12);
        assertThat(response.candidates().getFirst().hasAnalyticsData()).isTrue();
    }

    @Test
    void playerCandidateLookupFallsBackToPartialDisplayNameSearch() {
        var candidate = playerCandidate(OTHER_PROFILE_ID, "Aegis Ace", "aegis_ace", 5, null);
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(lookupRepository.findAnalyzedPlayerComparisonCandidates(
                eq(CURRENT_PROFILE_ID),
                eq("aegis"),
                any(),
                eq(true),
                eq(true),
                eq(10)))
                .thenReturn(List.of());
        when(lookupRepository.findAnalyzedPlayerComparisonCandidates(
                eq(CURRENT_PROFILE_ID),
                eq("aegis"),
                any(),
                eq(true),
                eq(false),
                eq(10)))
                .thenReturn(List.of(candidate));

        var response = service.playerComparisonCandidates(
                "aegis",
                new AnalyticsFilters(null, null, null, null, 10));

        assertThat(response.query()).isEqualTo("aegis");
        assertThat(response.exactMatch()).isFalse();
        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().getFirst().displayName()).isEqualTo("Aegis Ace");
    }

    @Test
    void playerCandidateLookupCanMatchNickname() {
        var candidate = playerCandidate(OTHER_PROFILE_ID, "Aegis Ace", "aegis_ace", 7, null);
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(lookupRepository.findAnalyzedPlayerComparisonCandidates(
                eq(CURRENT_PROFILE_ID),
                eq("aegis_ace"),
                any(),
                eq(true),
                eq(true),
                eq(10)))
                .thenReturn(List.of(candidate));

        var response = service.playerComparisonCandidates(
                "aegis_ace",
                new AnalyticsFilters(null, null, null, null, 10));

        assertThat(response.exactMatch()).isTrue();
        assertThat(response.candidates().getFirst().nickname()).isEqualTo("aegis_ace");
    }

    @Test
    void playerCandidateLookupNormalizesOpenDotaPlayerUrl() {
        var candidate = playerCandidate(OTHER_PROFILE_ID, "Aegis Ace", "aegis_ace", 7, 123456789L);
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(lookupRepository.findAnalyzedPlayerComparisonCandidates(
                eq(CURRENT_PROFILE_ID),
                eq("123456789"),
                any(),
                eq(true),
                eq(true),
                eq(10)))
                .thenReturn(List.of(candidate));

        var response = service.playerComparisonCandidates(
                "https://www.opendota.com/players/123456789",
                new AnalyticsFilters(null, null, null, null, 10));

        assertThat(response.query()).isEqualTo("123456789");
        assertThat(response.candidates().getFirst().opendotaAccountId()).isEqualTo(123456789L);
    }

    @Test
    void playerCandidateLookupCapsLimitAtTwenty() {
        var candidate = playerCandidate(OTHER_PROFILE_ID, "Aegis Ace", "aegis_ace", 7, null);
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(lookupRepository.findAnalyzedPlayerComparisonCandidates(
                eq(CURRENT_PROFILE_ID),
                eq("Aegis"),
                any(),
                eq(true),
                eq(true),
                eq(20)))
                .thenReturn(List.of(candidate));

        var response = service.playerComparisonCandidates(
                "Aegis",
                new AnalyticsFilters(null, null, null, null, 100));

        assertThat(response.candidates()).hasSize(1);
        verify(lookupRepository).findAnalyzedPlayerComparisonCandidates(
                eq(CURRENT_PROFILE_ID),
                eq("Aegis"),
                any(),
                eq(true),
                eq(true),
                eq(20));
    }

    @Test
    void playerCandidateLookupCanReturnTeamCandidateWithoutAnalyticsData() {
        var candidate = playerCandidate(OTHER_PROFILE_ID, "Bench Player", "bench", 0, null);
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(lookupRepository.isActiveTeamMember(TEAM_A_ID, CURRENT_PROFILE_ID)).thenReturn(true);
        when(lookupRepository.findActiveTeamPlayerComparisonCandidates(
                eq(TEAM_A_ID),
                eq(CURRENT_PROFILE_ID),
                eq("Bench"),
                eq(true),
                eq(10)))
                .thenReturn(List.of(candidate));

        var response = service.playerComparisonCandidates(
                "Bench",
                new AnalyticsFilters(null, TEAM_A_ID, null, null, 10));

        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().getFirst().hasAnalyticsData()).isFalse();
        assertThat(response.candidates().getFirst().analyticsGamesCount()).isZero();
        assertThat(response.candidates().getFirst().label()).isEqualTo("No imported matches yet");
    }

    @Test
    void playerCandidateLookupReturnsAmbiguousExactMatches() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(lookupRepository.findAnalyzedPlayerComparisonCandidates(
                eq(CURRENT_PROFILE_ID),
                eq("Carry"),
                any(),
                eq(true),
                eq(true),
                eq(10)))
                .thenReturn(List.of(
                        playerCandidate(OTHER_PROFILE_ID, "Carry", "safe_carry"),
                        playerCandidate(THIRD_PROFILE_ID, "Carry", "greedy_carry")));

        var response = service.playerComparisonCandidates(
                "Carry",
                new AnalyticsFilters(null, null, null, null, 10));

        assertThat(response.exactMatch()).isTrue();
        assertThat(response.ambiguous()).isTrue();
        assertThat(response.candidates())
                .extracting("profileId")
                .containsExactly(OTHER_PROFILE_ID, THIRD_PROFILE_ID);
    }

    @Test
    void playerCandidateLookupFallsBackToPartialSearchAndCanReturnNoMatches() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(lookupRepository.findAnalyzedPlayerComparisonCandidates(
                eq(CURRENT_PROFILE_ID),
                eq("Unknown"),
                any(),
                eq(true),
                eq(true),
                eq(10)))
                .thenReturn(List.of());
        when(lookupRepository.findAnalyzedPlayerComparisonCandidates(
                eq(CURRENT_PROFILE_ID),
                eq("Unknown"),
                any(),
                eq(true),
                eq(false),
                eq(10)))
                .thenReturn(List.of());

        var response = service.playerComparisonCandidates(
                "Unknown",
                new AnalyticsFilters(null, null, null, null, 10));

        assertThat(response.exactMatch()).isFalse();
        assertThat(response.ambiguous()).isFalse();
        assertThat(response.candidates()).isEmpty();
    }

    @Test
    void organizerCandidateLookupRequiresManageableTournament() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.ORGANIZER));
        when(tournamentRepository.canManage(TOURNAMENT_ID, CURRENT_PROFILE_ID, false)).thenReturn(false);

        assertThatThrownBy(() -> service.playerComparisonCandidates(
                "Carry",
                new AnalyticsFilters(TOURNAMENT_ID, null, null, null, 10)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only tournament organizers can compare private tournament analytics.");
    }

    @Test
    void organizerComparisonRequiresTournamentScope() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.ORGANIZER));

        assertThatThrownBy(() -> service.compareTeams(
                TEAM_A_ID,
                TEAM_B_ID,
                new AnalyticsFilters(null, null, null, null, 10)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Organizer comparisons require tournamentId.");
    }

    @Test
    void organizerCanCompareTeamsInsideManagedTournamentUsingProtectedScope() {
        HeroMetricsResponse heroMetrics = heroMetricsResponse();
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.ORGANIZER));
        when(tournamentRepository.canManage(TOURNAMENT_ID, CURRENT_PROFILE_ID, false)).thenReturn(true);
        when(analyticsQueryService.heroMetricsForTeams(eq(TEAM_A_ID), eq(TEAM_B_ID), any(), eq(false)))
                .thenReturn(List.of(heroMetrics));
        when(analyticsQueryService.recentMatchesForTeams(eq(TEAM_A_ID), eq(TEAM_B_ID), any(), eq(false)))
                .thenReturn(List.of());

        var response = service.compareTeams(
                TEAM_A_ID,
                TEAM_B_ID,
                new AnalyticsFilters(TOURNAMENT_ID, null, null, null, 10));

        assertThat(response.filters().accessScope()).isEqualTo("protected");
        assertThat(response.heroMetrics()).containsExactly(heroMetrics);
        verify(analyticsQueryService).teamAggregateMetrics(eq(TEAM_A_ID), any(), eq(false));
        verify(analyticsQueryService).teamAggregateMetrics(eq(TEAM_B_ID), any(), eq(false));
        verify(analyticsQueryService).heroMetricsForTeams(eq(TEAM_A_ID), eq(TEAM_B_ID), any(), eq(false));
    }

    private AuthenticatedActor actor(ProfileRole role) {
        return new AuthenticatedActor(AUTH_USER_ID, CURRENT_PROFILE_ID, "profile@example.test", null, role);
    }

    private HeroMetricsResponse heroMetricsResponse() {
        return new HeroMetricsResponse(
                HERO_ID,
                1,
                "antimage",
                "Anti-Mage",
                null,
                null,
                TOURNAMENT_ID,
                "International Test Cup",
                4,
                3,
                1,
                BigDecimal.valueOf(75),
                32,
                12,
                40,
                BigDecimal.valueOf(8),
                BigDecimal.valueOf(3),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(6),
                BigDecimal.valueOf(610),
                BigDecimal.valueOf(720),
                BigDecimal.valueOf(22000));
    }

    private PlayerComparisonMetricResponse comparisonMetric(
            UUID profileId,
            String displayName,
            int gamesPlayed,
            String avgGpm,
            String kda
    ) {
        return new PlayerComparisonMetricResponse(
                profileId,
                displayName,
                gamesPlayed,
                gamesPlayed - 2,
                2,
                new BigDecimal("75.00"),
                new BigDecimal(kda),
                new BigDecimal("8.00"),
                new BigDecimal("2.00"),
                new BigDecimal("10.00"),
                new BigDecimal(avgGpm),
                new BigDecimal("700.00"),
                new BigDecimal("210.00"),
                new BigDecimal("12.00"),
                new BigDecimal("20500.00"),
                new BigDecimal("23000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("200.00"));
    }

    private PlayerHeroPerformanceResponse heroDetail(
            int matches,
            String avgKda,
            String avgGpm,
            String avgXpm,
            String avgHeroDamage,
            String avgTowerDamage,
            String avgDeaths
    ) {
        return new PlayerHeroPerformanceResponse(
                HERO_ID,
                1,
                "Anti-Mage",
                matches,
                Math.max(matches - 1, 0),
                Math.min(matches, 1),
                new BigDecimal("75.00"),
                new BigDecimal("8.00"),
                new BigDecimal(avgDeaths),
                new BigDecimal("10.00"),
                new BigDecimal(avgKda),
                new BigDecimal(avgGpm),
                new BigDecimal(avgXpm),
                new BigDecimal(avgHeroDamage),
                new BigDecimal(avgTowerDamage),
                new BigDecimal("120.00"),
                new BigDecimal("210.00"),
                new BigDecimal("10.00"),
                MATCH_ID,
                null,
                "7777777777",
                OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                MATCH_ID,
                null,
                "7777777777",
                OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                new BigDecimal("9.00"));
    }

    private PlayerComparisonMatchResponse comparisonMatch() {
        return new PlayerComparisonMatchResponse(
                MATCH_ID,
                null,
                "7777777777",
                TOURNAMENT_ID,
                "International Test Cup",
                OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                TEAM_A_ID,
                "Radiant Wolves",
                TEAM_B_ID,
                "Dire Five",
                TEAM_A_ID,
                "RADIANT",
                new PlayerComparisonMatchPlayerResponse(
                        CURRENT_PROFILE_ID,
                        TEAM_A_ID,
                        "Radiant Wolves",
                        HERO_ID,
                        1,
                        "Anti-Mage",
                        true,
                        10,
                        2,
                        8,
                        new BigDecimal("9.00"),
                        640,
                        720,
                        240,
                        12,
                        21400,
                        24000,
                        2600,
                        0,
                        "RADIANT"),
                new PlayerComparisonMatchPlayerResponse(
                        OTHER_PROFILE_ID,
                        TEAM_B_ID,
                        "Dire Five",
                        HERO_ID,
                        1,
                        "Anti-Mage",
                        false,
                        7,
                        4,
                        9,
                        new BigDecimal("4.00"),
                        560,
                        660,
                        180,
                        8,
                        17200,
                        19000,
                        1800,
                        0,
                        "DIRE"));
    }

    private AnalyticsLookupRepository.PlayerComparisonCandidate playerCandidate(
            UUID profileId,
            String displayName,
            String nickname
    ) {
        return playerCandidate(profileId, displayName, nickname, 0, null);
    }

    private AnalyticsLookupRepository.PlayerComparisonCandidate playerCandidate(
            UUID profileId,
            String displayName,
            String nickname,
            int analyticsGamesCount,
            Long opendotaAccountId
    ) {
        return new AnalyticsLookupRepository.PlayerComparisonCandidate(
                profileId,
                displayName,
                nickname,
                TEAM_A_ID,
                "Radiant Wolves",
                "https://cdn.example.test/avatar.png",
                opendotaAccountId,
                analyticsGamesCount);
    }
}
