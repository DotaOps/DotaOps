package si.um.feri.dotaops.backend.analytics.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.um.feri.dotaops.backend.analytics.domain.AnalyticsFilters;
import si.um.feri.dotaops.backend.analytics.repository.AnalyticsLookupRepository;
import si.um.feri.dotaops.backend.analytics.web.AnalyticsComparisonFiltersResponse;
import si.um.feri.dotaops.backend.analytics.web.HeroMetricsResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerComparisonCandidateResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerComparisonLookupResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerComparisonResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerMetricsResponse;
import si.um.feri.dotaops.backend.analytics.web.TeamComparisonResponse;
import si.um.feri.dotaops.backend.analytics.web.TeamMetricsResponse;
import si.um.feri.dotaops.backend.auth.domain.AuthenticatedActor;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.tournament.repository.TournamentRepository;

@Service
public class AnalyticsComparisonService {

    private static final String ACCESS_SCOPE_PROTECTED = "protected";
    private static final String ACCESS_SCOPE_PUBLIC = "public";
    private static final int MIN_PLAYER_SEARCH_LENGTH = 2;
    private static final int MAX_PLAYER_SEARCH_LIMIT = 20;

    private final AnalyticsQueryService analyticsQueryService;
    private final AnalyticsLookupRepository lookupRepository;
    private final TournamentRepository tournamentRepository;
    private final CurrentUserProvider currentUserProvider;

    public AnalyticsComparisonService(
            AnalyticsQueryService analyticsQueryService,
            AnalyticsLookupRepository lookupRepository,
            TournamentRepository tournamentRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.analyticsQueryService = analyticsQueryService;
        this.lookupRepository = lookupRepository;
        this.tournamentRepository = tournamentRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public TeamComparisonResponse compareTeams(UUID teamAId, UUID teamBId, AnalyticsFilters requestedFilters) {
        if (teamAId.equals(teamBId)) {
            throw new BadRequestException("Team comparison requires two different teams.");
        }

        AuthenticatedActor actor = currentUserProvider.requireActor();
        ComparisonAccess access = teamComparisonAccess(actor, teamAId, teamBId, requestedFilters);
        AnalyticsFilters filters = new AnalyticsFilters(
                requestedFilters.tournamentId(),
                null,
                requestedFilters.profileId(),
                requestedFilters.heroId(),
                requestedFilters.from(),
                requestedFilters.to(),
                requestedFilters.limit());

        TeamMetricsResponse teamA = analyticsQueryService.teamAggregateMetrics(teamAId, filters, access.publicOnly())
                .orElse(null);
        TeamMetricsResponse teamB = analyticsQueryService.teamAggregateMetrics(teamBId, filters, access.publicOnly())
                .orElse(null);

        return new TeamComparisonResponse(
                teamAId,
                teamBId,
                AnalyticsComparisonFiltersResponse.from(filters, access.scope()),
                teamA,
                teamB,
                Stream.of(teamA, teamB).filter(Objects::nonNull).toList(),
                analyticsQueryService.heroMetricsForTeams(teamAId, teamBId, filters, access.publicOnly()),
                analyticsQueryService.recentMatchesForTeams(teamAId, teamBId, filters, access.publicOnly()));
    }

    @Transactional(readOnly = true)
    public PlayerComparisonResponse comparePlayers(
            UUID profileAId,
            UUID profileBId,
            AnalyticsFilters requestedFilters
    ) {
        if (profileAId.equals(profileBId)) {
            throw new BadRequestException("Player comparison requires two different players.");
        }

        AuthenticatedActor actor = currentUserProvider.requireActor();
        ComparisonAccess access = playerComparisonAccess(actor, profileAId, profileBId, requestedFilters);
        AnalyticsFilters filters = new AnalyticsFilters(
                requestedFilters.tournamentId(),
                requestedFilters.teamId(),
                null,
                requestedFilters.heroId(),
                requestedFilters.from(),
                requestedFilters.to(),
                requestedFilters.limit());

        PlayerMetricsResponse playerA = analyticsQueryService
                .playerAggregateMetrics(profileAId, filters, access.publicOnly())
                .orElse(null);
        PlayerMetricsResponse playerB = analyticsQueryService
                .playerAggregateMetrics(profileBId, filters, access.publicOnly())
                .orElse(null);
        List<HeroMetricsResponse> heroA = heroPerformance(profileAId, filters, access.publicOnly());
        List<HeroMetricsResponse> heroB = heroPerformance(profileBId, filters, access.publicOnly());

        return new PlayerComparisonResponse(
                profileAId,
                profileBId,
                AnalyticsComparisonFiltersResponse.from(filters, access.scope()),
                playerA,
                playerB,
                Stream.of(playerA, playerB).filter(Objects::nonNull).toList(),
                heroA,
                heroB,
                analyticsQueryService.sharedHeroesForPlayers(profileAId, profileBId, filters, access.publicOnly()),
                analyticsQueryService.recentMatchesForPlayers(profileAId, profileBId, filters, access.publicOnly()));
    }

    @Transactional(readOnly = true)
    public PlayerComparisonLookupResponse playerComparisonCandidates(
            String query,
            AnalyticsFilters requestedFilters
    ) {
        String normalizedQuery = normalizePlayerSearchQuery(query);
        AuthenticatedActor actor = currentUserProvider.requireActor();
        CandidateSearch search = playerCandidateSearch(actor, requestedFilters);
        int limit = Math.min(requestedFilters.limit(), MAX_PLAYER_SEARCH_LIMIT);

        List<AnalyticsLookupRepository.PlayerComparisonCandidate> exactCandidates =
                search.find(normalizedQuery, true, limit);
        boolean exactMatch = !exactCandidates.isEmpty();
        List<AnalyticsLookupRepository.PlayerComparisonCandidate> candidates = exactMatch
                ? exactCandidates
                : search.find(normalizedQuery, false, limit);

        List<PlayerComparisonCandidateResponse> responses = candidates.stream()
                .map(PlayerComparisonCandidateResponse::from)
                .toList();

        return new PlayerComparisonLookupResponse(
                normalizedQuery,
                exactMatch,
                responses.size() > 1,
                responses);
    }

    private List<HeroMetricsResponse> heroPerformance(UUID profileId, AnalyticsFilters filters, boolean publicOnly) {
        AnalyticsFilters scopedFilters = filters.withProfileId(profileId);
        return publicOnly
                ? analyticsQueryService.heroMetrics(scopedFilters)
                : analyticsQueryService.protectedHeroMetrics(scopedFilters);
    }

    private ComparisonAccess teamComparisonAccess(
            AuthenticatedActor actor,
            UUID teamAId,
            UUID teamBId,
            AnalyticsFilters filters
    ) {
        UUID profileId = actor.requireProfileId();
        if (actor.isAdmin()) {
            return ComparisonAccess.protectedScope();
        }
        if (actor.role() == ProfileRole.ORGANIZER) {
            requireOrganizerTournamentScope(actor, filters.tournamentId());
            return ComparisonAccess.protectedScope();
        }
        if (actor.role() == ProfileRole.PLAYER
                && (lookupRepository.isActiveTeamMember(teamAId, profileId)
                || lookupRepository.isActiveTeamMember(teamBId, profileId))) {
            return ComparisonAccess.publicScope();
        }

        throw new AccessDeniedException("You cannot compare these teams.");
    }

    private ComparisonAccess playerComparisonAccess(
            AuthenticatedActor actor,
            UUID profileAId,
            UUID profileBId,
            AnalyticsFilters filters
    ) {
        UUID profileId = actor.requireProfileId();
        if (actor.isAdmin()) {
            return ComparisonAccess.protectedScope();
        }
        if (actor.role() == ProfileRole.ORGANIZER) {
            requireOrganizerTournamentScope(actor, filters.tournamentId());
            return ComparisonAccess.protectedScope();
        }
        if (actor.role() == ProfileRole.PLAYER
                && lookupRepository.teamsShareActiveMembership(profileId, profileAId, profileBId)) {
            return ComparisonAccess.protectedScope();
        }
        if (actor.role() == ProfileRole.PLAYER
                && (profileId.equals(profileAId) || profileId.equals(profileBId))) {
            return ComparisonAccess.publicScope();
        }

        throw new AccessDeniedException("You cannot compare these players.");
    }

    private CandidateSearch playerCandidateSearch(AuthenticatedActor actor, AnalyticsFilters requestedFilters) {
        UUID profileId = actor.requireProfileId();
        AnalyticsFilters filters = new AnalyticsFilters(
                requestedFilters.tournamentId(),
                requestedFilters.teamId(),
                null,
                requestedFilters.heroId(),
                requestedFilters.from(),
                requestedFilters.to(),
                requestedFilters.limit());

        if (actor.isAdmin()) {
            return (query, exact, limit) -> lookupRepository.findAnalyzedPlayerComparisonCandidates(
                    null,
                    query,
                    filters,
                    false,
                    exact,
                    limit);
        }

        if (actor.role() == ProfileRole.ORGANIZER) {
            requireOrganizerTournamentScope(actor, filters.tournamentId());
            return (query, exact, limit) -> lookupRepository.findAnalyzedPlayerComparisonCandidates(
                    null,
                    query,
                    filters,
                    false,
                    exact,
                    limit);
        }

        if (actor.role() == ProfileRole.PLAYER
                && filters.teamId() != null
                && lookupRepository.isActiveTeamMember(filters.teamId(), profileId)) {
            return (query, exact, limit) -> lookupRepository.findActiveTeamPlayerComparisonCandidates(
                    filters.teamId(),
                    profileId,
                    query,
                    exact,
                    limit);
        }

        if (actor.role() == ProfileRole.PLAYER) {
            return (query, exact, limit) -> lookupRepository.findAnalyzedPlayerComparisonCandidates(
                    profileId,
                    query,
                    filters.withTeamId(null),
                    true,
                    exact,
                    limit);
        }

        throw new AccessDeniedException("You cannot search player comparison candidates.");
    }

    private String normalizePlayerSearchQuery(String query) {
        String normalized = query == null ? "" : query.trim().replaceAll("\\s+", " ");
        if (normalized.length() < MIN_PLAYER_SEARCH_LENGTH) {
            throw new BadRequestException("Player search query must contain at least 2 characters.");
        }

        return normalized;
    }

    private void requireOrganizerTournamentScope(AuthenticatedActor actor, UUID tournamentId) {
        if (tournamentId == null) {
            throw new BadRequestException("Organizer comparisons require tournamentId.");
        }
        if (!tournamentRepository.canManage(tournamentId, actor.requireProfileId(), actor.isAdmin())) {
            throw new AccessDeniedException("Only tournament organizers can compare private tournament analytics.");
        }
    }

    @FunctionalInterface
    private interface CandidateSearch {
        List<AnalyticsLookupRepository.PlayerComparisonCandidate> find(String query, boolean exact, int limit);
    }

    private record ComparisonAccess(String scope, boolean publicOnly) {

        static ComparisonAccess protectedScope() {
            return new ComparisonAccess(ACCESS_SCOPE_PROTECTED, false);
        }

        static ComparisonAccess publicScope() {
            return new ComparisonAccess(ACCESS_SCOPE_PUBLIC, true);
        }
    }
}
