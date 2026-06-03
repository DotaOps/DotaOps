package si.um.feri.dotaops.backend.analytics.service;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import si.um.feri.dotaops.backend.analytics.domain.AnalyticsFilters;
import si.um.feri.dotaops.backend.analytics.repository.AnalyticsLookupRepository;
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
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.ORGANIZER));
        when(tournamentRepository.canManage(TOURNAMENT_ID, CURRENT_PROFILE_ID, false)).thenReturn(true);
        when(analyticsQueryService.recentMatchesForTeams(eq(TEAM_A_ID), eq(TEAM_B_ID), any(), eq(false)))
                .thenReturn(List.of());

        var response = service.compareTeams(
                TEAM_A_ID,
                TEAM_B_ID,
                new AnalyticsFilters(TOURNAMENT_ID, null, null, null, 10));

        assertThat(response.filters().accessScope()).isEqualTo("protected");
        verify(analyticsQueryService).teamAggregateMetrics(eq(TEAM_A_ID), any(), eq(false));
        verify(analyticsQueryService).teamAggregateMetrics(eq(TEAM_B_ID), any(), eq(false));
    }

    private AuthenticatedActor actor(ProfileRole role) {
        return new AuthenticatedActor(AUTH_USER_ID, CURRENT_PROFILE_ID, "profile@example.test", null, role);
    }
}
