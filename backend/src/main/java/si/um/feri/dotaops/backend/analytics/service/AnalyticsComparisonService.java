package si.um.feri.dotaops.backend.analytics.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.um.feri.dotaops.backend.analytics.domain.AnalyticsFilters;
import si.um.feri.dotaops.backend.analytics.repository.AnalyticsLookupRepository;
import si.um.feri.dotaops.backend.analytics.web.AnalyticsComparisonFiltersResponse;
import si.um.feri.dotaops.backend.analytics.web.HeroMetricsResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerComparisonCandidateResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerComparisonHeadlineResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerComparisonHeroDeltaResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerComparisonHeroStatsResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerComparisonLookupResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerComparisonMatchResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerComparisonMetricDeltaResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerComparisonMetricResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerComparisonResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerComparisonSharedHeroResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerComparisonWarningResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerHeroPerformanceResponse;
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
    private static final int MIN_PLAYER_COMPARISON_SAMPLE_SIZE = 5;
    private static final int MIN_SHARED_HERO_SAMPLE_SIZE = 3;
    private static final Pattern OPENDOTA_PLAYER_URL_PATTERN =
            Pattern.compile("(?i)opendota\\.com/players/(\\d+)");

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
        PlayerComparisonMetricResponse headlineA = headlineMetrics(profileAId, filters, access.publicOnly());
        PlayerComparisonMetricResponse headlineB = headlineMetrics(profileBId, filters, access.publicOnly());
        List<HeroMetricsResponse> heroA = heroPerformance(profileAId, filters, access.publicOnly());
        List<HeroMetricsResponse> heroB = heroPerformance(profileBId, filters, access.publicOnly());
        List<PlayerHeroPerformanceResponse> heroDetailsA = playerHeroDetails(profileAId, filters, access.publicOnly());
        List<PlayerHeroPerformanceResponse> heroDetailsB = playerHeroDetails(profileBId, filters, access.publicOnly());
        List<PlayerComparisonSharedHeroResponse> sharedHeroComparisons = sharedHeroComparisons(
                profileAId,
                profileBId,
                heroDetailsA,
                heroDetailsB);
        List<PlayerComparisonMatchResponse> enrichedMatchHistory = safeList(analyticsQueryService
                .playerComparisonMatches(profileAId, profileBId, filters, access.publicOnly()));
        List<PlayerComparisonWarningResponse> warnings = comparisonWarnings(
                profileAId,
                profileBId,
                headlineA,
                headlineB,
                sharedHeroComparisons);

        return new PlayerComparisonResponse(
                profileAId,
                profileBId,
                AnalyticsComparisonFiltersResponse.from(filters, access.scope()),
                playerA,
                playerB,
                Stream.of(playerA, playerB).filter(Objects::nonNull).toList(),
                heroA,
                heroB,
                safeList(analyticsQueryService.sharedHeroesForPlayers(
                        profileAId,
                        profileBId,
                        filters,
                        access.publicOnly())),
                safeList(analyticsQueryService.recentMatchesForPlayers(
                        profileAId,
                        profileBId,
                        filters,
                        access.publicOnly())),
                new PlayerComparisonHeadlineResponse(
                        headlineA,
                        headlineB,
                        headlineDelta(headlineA, headlineB)),
                heroDetailsA,
                heroDetailsB,
                sharedHeroComparisons,
                enrichedMatchHistory,
                warnings);
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
        return safeList(publicOnly
                ? analyticsQueryService.heroMetrics(scopedFilters)
                : analyticsQueryService.protectedHeroMetrics(scopedFilters));
    }

    private PlayerComparisonMetricResponse headlineMetrics(
            UUID profileId,
            AnalyticsFilters filters,
            boolean publicOnly
    ) {
        Optional<PlayerComparisonMetricResponse> metrics = analyticsQueryService
                .playerComparisonHeadlineMetrics(profileId, filters, publicOnly);
        return metrics == null ? null : metrics.orElse(null);
    }

    private List<PlayerHeroPerformanceResponse> playerHeroDetails(
            UUID profileId,
            AnalyticsFilters filters,
            boolean publicOnly
    ) {
        return safeList(analyticsQueryService.playerHeroPerformance(profileId, filters, publicOnly));
    }

    private List<PlayerComparisonSharedHeroResponse> sharedHeroComparisons(
            UUID profileAId,
            UUID profileBId,
            List<PlayerHeroPerformanceResponse> profileAHeroDetails,
            List<PlayerHeroPerformanceResponse> profileBHeroDetails
    ) {
        Map<UUID, PlayerHeroPerformanceResponse> profileBByHero = profileBHeroDetails.stream()
                .filter(hero -> hero.heroId() != null)
                .collect(LinkedHashMap::new, (map, hero) -> map.putIfAbsent(hero.heroId(), hero), Map::putAll);

        return profileAHeroDetails.stream()
                .filter(hero -> hero.heroId() != null)
                .map(heroA -> sharedHeroComparison(profileAId, profileBId, heroA, profileBByHero.get(heroA.heroId())))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(this::sharedHeroSampleSize).reversed())
                .toList();
    }

    private PlayerComparisonSharedHeroResponse sharedHeroComparison(
            UUID profileAId,
            UUID profileBId,
            PlayerHeroPerformanceResponse heroA,
            PlayerHeroPerformanceResponse heroB
    ) {
        if (heroB == null) {
            return null;
        }

        PlayerComparisonHeroStatsResponse profileAStats = heroStats(profileAId, heroA);
        PlayerComparisonHeroStatsResponse profileBStats = heroStats(profileBId, heroB);
        return new PlayerComparisonSharedHeroResponse(
                heroA.heroId(),
                heroA.dotaHeroId() != null ? heroA.dotaHeroId() : heroB.dotaHeroId(),
                firstNonBlank(heroA.heroName(), heroB.heroName()),
                profileAStats,
                profileBStats,
                heroDelta(profileAStats, profileBStats));
    }

    private int sharedHeroSampleSize(PlayerComparisonSharedHeroResponse hero) {
        return hero.profileA().gamesPlayed() + hero.profileB().gamesPlayed();
    }

    private PlayerComparisonHeroStatsResponse heroStats(UUID profileId, PlayerHeroPerformanceResponse hero) {
        return new PlayerComparisonHeroStatsResponse(
                profileId,
                hero.matches(),
                hero.wins(),
                hero.losses(),
                hero.winRate(),
                hero.avgKda(),
                hero.avgKills(),
                hero.avgDeaths(),
                hero.avgAssists(),
                hero.avgGpm(),
                hero.avgXpm(),
                hero.avgHeroDamage(),
                hero.avgTowerDamage(),
                hero.avgHeroHealing());
    }

    private PlayerComparisonHeroDeltaResponse heroDelta(
            PlayerComparisonHeroStatsResponse profileA,
            PlayerComparisonHeroStatsResponse profileB
    ) {
        return new PlayerComparisonHeroDeltaResponse(
                profileA.gamesPlayed() - profileB.gamesPlayed(),
                delta(profileA.winRate(), profileB.winRate()),
                delta(profileA.kda(), profileB.kda()),
                delta(profileA.avgDeaths(), profileB.avgDeaths()),
                delta(profileA.avgGpm(), profileB.avgGpm()),
                delta(profileA.avgXpm(), profileB.avgXpm()),
                delta(profileA.avgHeroDamage(), profileB.avgHeroDamage()),
                delta(profileA.avgTowerDamage(), profileB.avgTowerDamage()));
    }

    private PlayerComparisonMetricDeltaResponse headlineDelta(
            PlayerComparisonMetricResponse profileA,
            PlayerComparisonMetricResponse profileB
    ) {
        if (profileA == null || profileB == null) {
            return null;
        }

        return new PlayerComparisonMetricDeltaResponse(
                profileA.gamesPlayed() - profileB.gamesPlayed(),
                profileA.wins() - profileB.wins(),
                profileA.losses() - profileB.losses(),
                delta(profileA.winRate(), profileB.winRate()),
                delta(profileA.kda(), profileB.kda()),
                delta(profileA.avgKills(), profileB.avgKills()),
                delta(profileA.avgDeaths(), profileB.avgDeaths()),
                delta(profileA.avgAssists(), profileB.avgAssists()),
                delta(profileA.avgGpm(), profileB.avgGpm()),
                delta(profileA.avgXpm(), profileB.avgXpm()),
                delta(profileA.avgLastHits(), profileB.avgLastHits()),
                delta(profileA.avgDenies(), profileB.avgDenies()),
                delta(profileA.avgNetWorth(), profileB.avgNetWorth()),
                delta(profileA.avgHeroDamage(), profileB.avgHeroDamage()),
                delta(profileA.avgTowerDamage(), profileB.avgTowerDamage()),
                delta(profileA.avgHeroHealing(), profileB.avgHeroHealing()));
    }

    private List<PlayerComparisonWarningResponse> comparisonWarnings(
            UUID profileAId,
            UUID profileBId,
            PlayerComparisonMetricResponse headlineA,
            PlayerComparisonMetricResponse headlineB,
            List<PlayerComparisonSharedHeroResponse> sharedHeroComparisons
    ) {
        List<PlayerComparisonWarningResponse> warnings = new ArrayList<>();
        addPlayerSampleWarning(warnings, profileAId, headlineA);
        addPlayerSampleWarning(warnings, profileBId, headlineB);

        if (sharedHeroComparisons.isEmpty()) {
            warnings.add(new PlayerComparisonWarningResponse(
                    "NO_SHARED_HERO_SAMPLE",
                    "INFO",
                    "No shared hero sample is available for these filters.",
                    null,
                    null,
                    "sharedHeroes",
                    0,
                    MIN_SHARED_HERO_SAMPLE_SIZE));
        }

        sharedHeroComparisons.stream()
                .filter(hero -> Math.min(hero.profileA().gamesPlayed(), hero.profileB().gamesPlayed())
                        < MIN_SHARED_HERO_SAMPLE_SIZE)
                .map(hero -> new PlayerComparisonWarningResponse(
                        "LOW_SHARED_HERO_SAMPLE",
                        "WARNING",
                        "Shared hero comparison for " + firstNonBlank(hero.heroName(), "this hero")
                                + " has a small sample.",
                        null,
                        hero.heroId(),
                        "sharedHeroes",
                        Math.min(hero.profileA().gamesPlayed(), hero.profileB().gamesPlayed()),
                        MIN_SHARED_HERO_SAMPLE_SIZE))
                .limit(5)
                .forEach(warnings::add);

        return warnings;
    }

    private void addPlayerSampleWarning(
            List<PlayerComparisonWarningResponse> warnings,
            UUID profileId,
            PlayerComparisonMetricResponse headline
    ) {
        int sampleSize = headline == null ? 0 : headline.gamesPlayed();
        if (sampleSize >= MIN_PLAYER_COMPARISON_SAMPLE_SIZE) {
            return;
        }

        warnings.add(new PlayerComparisonWarningResponse(
                "LOW_PLAYER_SAMPLE",
                "WARNING",
                "Headline comparison for this player has a small sample.",
                profileId,
                null,
                "headline",
                sampleSize,
                MIN_PLAYER_COMPARISON_SAMPLE_SIZE));
    }

    private BigDecimal delta(BigDecimal profileAValue, BigDecimal profileBValue) {
        return zero(profileAValue).subtract(zero(profileBValue));
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
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
        Matcher openDotaMatcher = OPENDOTA_PLAYER_URL_PATTERN.matcher(normalized);
        if (openDotaMatcher.find()) {
            normalized = openDotaMatcher.group(1);
        }
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
