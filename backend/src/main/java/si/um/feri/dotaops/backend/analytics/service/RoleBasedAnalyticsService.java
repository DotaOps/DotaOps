package si.um.feri.dotaops.backend.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.um.feri.dotaops.backend.analytics.domain.AnalyticsFilters;
import si.um.feri.dotaops.backend.analytics.repository.RoleBasedAnalyticsRepository;
import si.um.feri.dotaops.backend.analytics.web.CurrentTeamAnalyticsResponse;
import si.um.feri.dotaops.backend.analytics.web.OrganizerAnalyticsResponse;
import si.um.feri.dotaops.backend.analytics.web.OrganizerTournamentAnalyticsResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerAnalyticsResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerHeroPerformanceResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerInsightCategory;
import si.um.feri.dotaops.backend.analytics.web.PlayerInsightResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerProgressPointResponse;
import si.um.feri.dotaops.backend.analytics.web.RecentImportResponse;
import si.um.feri.dotaops.backend.auth.domain.AuthenticatedActor;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.ResourceNotFoundException;
import si.um.feri.dotaops.backend.team.domain.Team;
import si.um.feri.dotaops.backend.team.repository.TeamMemberRepository;
import si.um.feri.dotaops.backend.team.repository.TeamRepository;
import si.um.feri.dotaops.backend.team.web.TeamResponse;
import si.um.feri.dotaops.backend.tournament.repository.TournamentRepository;

@Service
public class RoleBasedAnalyticsService {

    private static final int RECENT_IMPORTS = 10;
    private static final int INSIGHT_LIMIT = 5;
    private static final int TREND_WINDOW = 3;
    private static final int MIN_TREND_SAMPLE_SIZE = TREND_WINDOW * 2;
    private static final int MIN_HERO_SAMPLE_SIZE = 3;
    private static final BigDecimal KDA_TREND_THRESHOLD = new BigDecimal("0.50");
    private static final BigDecimal ECONOMY_TREND_THRESHOLD = new BigDecimal("40.00");
    private static final BigDecimal HERO_DEATHS_THRESHOLD = new BigDecimal("1.50");
    private static final BigDecimal HERO_KDA_THRESHOLD = new BigDecimal("1.00");
    private static final BigDecimal HERO_WIN_RATE_THRESHOLD = new BigDecimal("20.00");

    private final AnalyticsQueryService analyticsQueryService;
    private final RoleBasedAnalyticsRepository roleBasedAnalyticsRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TournamentRepository tournamentRepository;
    private final CurrentUserProvider currentUserProvider;

    public RoleBasedAnalyticsService(
            AnalyticsQueryService analyticsQueryService,
            RoleBasedAnalyticsRepository roleBasedAnalyticsRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TournamentRepository tournamentRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.analyticsQueryService = analyticsQueryService;
        this.roleBasedAnalyticsRepository = roleBasedAnalyticsRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.tournamentRepository = tournamentRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public PlayerAnalyticsResponse currentPlayerAnalytics() {
        return currentPlayerAnalytics(new AnalyticsFilters(null, null, null, null, AnalyticsFilters.DEFAULT_LIMIT));
    }

    @Transactional(readOnly = true)
    public PlayerAnalyticsResponse currentPlayerAnalytics(AnalyticsFilters requestedFilters) {
        AuthenticatedActor actor = requirePlayer();
        UUID profileId = actor.requireProfileId();
        AnalyticsFilters filters = playerScopedFilters(requestedFilters, profileId);

        return new PlayerAnalyticsResponse(
                analyticsQueryService.protectedPlayerMetrics(filters),
                analyticsQueryService.protectedHeroMetrics(filters),
                analyticsQueryService.recentMatchesForPlayer(profileId, filters, false));
    }

    @Transactional(readOnly = true)
    public List<PlayerProgressPointResponse> currentPlayerProgress(AnalyticsFilters requestedFilters) {
        AuthenticatedActor actor = requirePlayer();
        UUID profileId = actor.requireProfileId();
        AnalyticsFilters filters = playerScopedFilters(requestedFilters, profileId);

        return analyticsQueryService.playerProgress(profileId, filters, false);
    }

    @Transactional(readOnly = true)
    public List<PlayerHeroPerformanceResponse> currentPlayerHeroPerformance(AnalyticsFilters requestedFilters) {
        AuthenticatedActor actor = requirePlayer();
        UUID profileId = actor.requireProfileId();
        AnalyticsFilters filters = playerScopedFilters(requestedFilters, profileId);

        return analyticsQueryService.playerHeroPerformance(profileId, filters, false);
    }

    @Transactional(readOnly = true)
    public List<PlayerInsightResponse> currentPlayerInsights(AnalyticsFilters requestedFilters) {
        AuthenticatedActor actor = requirePlayer();
        UUID profileId = actor.requireProfileId();
        AnalyticsFilters filters = playerScopedFilters(requestedFilters, profileId);

        List<PlayerProgressPointResponse> progress = analyticsQueryService.playerProgress(profileId, filters, false);
        List<PlayerHeroPerformanceResponse> heroPerformance =
                analyticsQueryService.playerHeroPerformance(profileId, filters, false);

        return buildPlayerInsights(progress, heroPerformance);
    }

    @Transactional(readOnly = true)
    public CurrentTeamAnalyticsResponse currentTeamAnalytics() {
        return currentTeamAnalytics(new AnalyticsFilters(null, null, null, null, AnalyticsFilters.DEFAULT_LIMIT));
    }

    @Transactional(readOnly = true)
    public CurrentTeamAnalyticsResponse currentTeamAnalytics(AnalyticsFilters requestedFilters) {
        AuthenticatedActor actor = requirePlayer();

        return teamRepository.findCurrentTeamForProfile(actor.requireProfileId())
                .map(team -> {
                    ensureRequestedTeamIsCurrent(requestedFilters.teamId(), team);
                    ensureRequestedProfileIsTeamMember(requestedFilters.profileId(), team);
                    AnalyticsFilters filters = new AnalyticsFilters(
                            requestedFilters.tournamentId(),
                            team.id(),
                            requestedFilters.profileId(),
                            requestedFilters.heroId(),
                            requestedFilters.from(),
                            requestedFilters.to(),
                            requestedFilters.limit());
                    return new CurrentTeamAnalyticsResponse(
                            TeamResponse.from(team),
                            analyticsQueryService.protectedTeamMetrics(filters),
                            analyticsQueryService.protectedPlayerMetrics(filters),
                            analyticsQueryService.recentMatchesForTeam(team.id(), filters, false));
                })
                .orElseGet(() -> {
                    if (requestedFilters.teamId() != null) {
                        throw new AccessDeniedException("Players can only view analytics for their current team.");
                    }
                    return new CurrentTeamAnalyticsResponse(null, List.of(), List.of(), List.of());
                });
    }

    @Transactional(readOnly = true)
    public OrganizerAnalyticsResponse organizerAnalytics() {
        return organizerAnalytics(new AnalyticsFilters(null, null, null, null, AnalyticsFilters.DEFAULT_LIMIT));
    }

    @Transactional(readOnly = true)
    public OrganizerAnalyticsResponse organizerAnalytics(AnalyticsFilters filters) {
        AuthenticatedActor actor = requireOrganizerOrAdmin();
        var counts = roleBasedAnalyticsRepository.findOrganizerCounts(actor.requireProfileId(), actor.isAdmin(), filters);

        return new OrganizerAnalyticsResponse(
                counts.tournaments(),
                counts.pendingRegistrations(),
                counts.approvedRegistrations(),
                counts.activePublishedTournaments(),
                counts.processedMatchGames(),
                counts.importJobs());
    }

    @Transactional(readOnly = true)
    public OrganizerTournamentAnalyticsResponse organizerTournamentAnalytics(UUID tournamentId) {
        return organizerTournamentAnalytics(
                tournamentId,
                new AnalyticsFilters(tournamentId, null, null, null, AnalyticsFilters.DEFAULT_LIMIT));
    }

    @Transactional(readOnly = true)
    public OrganizerTournamentAnalyticsResponse organizerTournamentAnalytics(
            UUID tournamentId,
            AnalyticsFilters requestedFilters
    ) {
        AuthenticatedActor actor = requireOrganizerOrAdmin();
        if (requestedFilters.tournamentId() != null && !requestedFilters.tournamentId().equals(tournamentId)) {
            throw new BadRequestException("Tournament filter does not match the route tournament.");
        }
        tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));
        if (!tournamentRepository.canManage(tournamentId, actor.requireProfileId(), actor.isAdmin())) {
            throw new AccessDeniedException("Only tournament organizers can view private tournament analytics.");
        }

        AnalyticsFilters filters = requestedFilters.withTournamentId(tournamentId);
        var operations = roleBasedAnalyticsRepository.findTournamentOperationalMetrics(tournamentId, filters);
        var teamMetrics = analyticsQueryService.protectedTeamMetrics(filters);

        return new OrganizerTournamentAnalyticsResponse(
                tournamentId,
                operations.gamesProcessed(),
                operations.matchesWithoutImport(),
                operations.importCoveragePercent(),
                operations.avgDurationSeconds(),
                analyticsQueryService.protectedTournamentMetrics(filters).orElse(null),
                teamMetrics,
                analyticsQueryService.protectedHeroMetrics(filters),
                teamMetrics,
                roleBasedAnalyticsRepository.findRecentImports(tournamentId, filters.withLimit(RECENT_IMPORTS))
                        .stream()
                        .map(RecentImportResponse::from)
                        .toList());
    }

    private void ensureRequestedTeamIsCurrent(UUID requestedTeamId, Team currentTeam) {
        if (requestedTeamId != null && !requestedTeamId.equals(currentTeam.id())) {
            throw new AccessDeniedException("Players can only view analytics for their current team.");
        }
    }

    private void ensureRequestedProfileIsTeamMember(UUID requestedProfileId, Team currentTeam) {
        if (requestedProfileId == null) {
            return;
        }
        if (requestedProfileId.equals(currentTeam.captainProfileId())
                || teamMemberRepository.existsActive(currentTeam.id(), requestedProfileId)) {
            return;
        }

        throw new AccessDeniedException("Players can only filter team analytics to active members of their team.");
    }

    private AnalyticsFilters playerScopedFilters(AnalyticsFilters requestedFilters, UUID profileId) {
        if (requestedFilters.profileId() != null && !requestedFilters.profileId().equals(profileId)) {
            throw new AccessDeniedException("Players can only view their own private analytics.");
        }

        return new AnalyticsFilters(
                requestedFilters.tournamentId(),
                requestedFilters.teamId(),
                profileId,
                requestedFilters.heroId(),
                requestedFilters.from(),
                requestedFilters.to(),
                requestedFilters.limit());
    }

    private List<PlayerInsightResponse> buildPlayerInsights(
            List<PlayerProgressPointResponse> progress,
            List<PlayerHeroPerformanceResponse> heroPerformance
    ) {
        List<PlayerInsightResponse> insights = new ArrayList<>();
        List<PlayerProgressPointResponse> chronologicalProgress = chronologicalProgress(progress);

        addKdaTrendInsight(insights, chronologicalProgress);
        addEconomyTrendInsight(insights, chronologicalProgress);
        addHighDeathsHeroInsight(insights, chronologicalProgress, heroPerformance);
        addHeroKdaInsight(insights, chronologicalProgress, heroPerformance);
        addHeroWinRateInsight(insights, heroPerformance);

        return insights.stream()
                .limit(INSIGHT_LIMIT)
                .toList();
    }

    private void addKdaTrendInsight(
            List<PlayerInsightResponse> insights,
            List<PlayerProgressPointResponse> progress
    ) {
        if (progress.size() < MIN_TREND_SAMPLE_SIZE) {
            return;
        }

        List<PlayerProgressPointResponse> previous = previousTrendWindow(progress);
        List<PlayerProgressPointResponse> recent = recentTrendWindow(progress);
        Optional<BigDecimal> previousAverage = averageDecimals(previous.stream()
                .map(PlayerProgressPointResponse::kda)
                .toList());
        Optional<BigDecimal> recentAverage = averageDecimals(recent.stream()
                .map(PlayerProgressPointResponse::kda)
                .toList());
        if (previousAverage.isEmpty() || recentAverage.isEmpty()) {
            return;
        }

        BigDecimal improvement = recentAverage.get().subtract(previousAverage.get());
        if (improvement.compareTo(KDA_TREND_THRESHOLD) < 0) {
            return;
        }

        insights.add(new PlayerInsightResponse(
                "KDA trend is improving",
                "Your average KDA over the last " + TREND_WINDOW + " matches is "
                        + formatDecimal(recentAverage.get()) + ", up from "
                        + formatDecimal(previousAverage.get()) + " in the previous " + TREND_WINDOW + ".",
                PlayerInsightCategory.POSITIVE,
                "KDA",
                scaleMetric(recentAverage.get()),
                scaleMetric(previousAverage.get()),
                MIN_TREND_SAMPLE_SIZE,
                "Compared " + TREND_WINDOW + " recent matches against the previous " + TREND_WINDOW + "."));
    }

    private void addEconomyTrendInsight(
            List<PlayerInsightResponse> insights,
            List<PlayerProgressPointResponse> progress
    ) {
        if (progress.size() < MIN_TREND_SAMPLE_SIZE) {
            return;
        }

        List<PlayerProgressPointResponse> previous = previousTrendWindow(progress);
        List<PlayerProgressPointResponse> recent = recentTrendWindow(progress);

        String metricName = null;
        BigDecimal currentValue = null;
        BigDecimal comparisonValue = null;
        BigDecimal largestDrop = BigDecimal.ZERO;

        Optional<BigDecimal> previousGpm = averageIntegers(previous.stream()
                .map(PlayerProgressPointResponse::goldPerMin)
                .toList());
        Optional<BigDecimal> recentGpm = averageIntegers(recent.stream()
                .map(PlayerProgressPointResponse::goldPerMin)
                .toList());
        if (previousGpm.isPresent() && recentGpm.isPresent()) {
            BigDecimal drop = previousGpm.get().subtract(recentGpm.get());
            if (drop.compareTo(ECONOMY_TREND_THRESHOLD) >= 0) {
                metricName = "GPM";
                currentValue = recentGpm.get();
                comparisonValue = previousGpm.get();
                largestDrop = drop;
            }
        }

        Optional<BigDecimal> previousXpm = averageIntegers(previous.stream()
                .map(PlayerProgressPointResponse::xpPerMin)
                .toList());
        Optional<BigDecimal> recentXpm = averageIntegers(recent.stream()
                .map(PlayerProgressPointResponse::xpPerMin)
                .toList());
        if (previousXpm.isPresent() && recentXpm.isPresent()) {
            BigDecimal drop = previousXpm.get().subtract(recentXpm.get());
            if (drop.compareTo(ECONOMY_TREND_THRESHOLD) >= 0 && drop.compareTo(largestDrop) > 0) {
                metricName = "XPM";
                currentValue = recentXpm.get();
                comparisonValue = previousXpm.get();
            }
        }

        if (metricName == null) {
            return;
        }

        insights.add(new PlayerInsightResponse(
                "Recent " + metricName + " trend is declining",
                "Your average " + metricName + " over the last " + TREND_WINDOW + " matches is "
                        + formatDecimal(currentValue) + ", down from "
                        + formatDecimal(comparisonValue) + " in the previous " + TREND_WINDOW + ".",
                PlayerInsightCategory.WARNING,
                metricName,
                scaleMetric(currentValue),
                scaleMetric(comparisonValue),
                MIN_TREND_SAMPLE_SIZE,
                "Compared " + TREND_WINDOW + " recent matches against the previous " + TREND_WINDOW + "."));
    }

    private void addHighDeathsHeroInsight(
            List<PlayerInsightResponse> insights,
            List<PlayerProgressPointResponse> progress,
            List<PlayerHeroPerformanceResponse> heroPerformance
    ) {
        Optional<BigDecimal> playerAverageDeaths = playerAverageDeaths(progress, heroPerformance);
        if (playerAverageDeaths.isEmpty()) {
            return;
        }

        heroPerformance.stream()
                .filter(hero -> hero.matches() >= MIN_HERO_SAMPLE_SIZE)
                .filter(hero -> hero.avgDeaths() != null)
                .map(hero -> new HeroInsightCandidate(hero, hero.avgDeaths().subtract(playerAverageDeaths.get())))
                .filter(candidate -> candidate.delta().compareTo(HERO_DEATHS_THRESHOLD) >= 0)
                .max(Comparator.comparing(HeroInsightCandidate::delta))
                .ifPresent(candidate -> insights.add(new PlayerInsightResponse(
                        "Deaths are high on " + heroName(candidate.hero()),
                        "Your average deaths on " + heroName(candidate.hero()) + " are "
                                + formatDecimal(candidate.hero().avgDeaths()) + ", above your filtered average of "
                                + formatDecimal(playerAverageDeaths.get()) + ".",
                        PlayerInsightCategory.WARNING,
                        "avgDeaths",
                        scaleMetric(candidate.hero().avgDeaths()),
                        scaleMetric(playerAverageDeaths.get()),
                        candidate.hero().matches(),
                        candidate.hero().matches() + " matches on " + heroName(candidate.hero()) + ".")));
    }

    private void addHeroKdaInsight(
            List<PlayerInsightResponse> insights,
            List<PlayerProgressPointResponse> progress,
            List<PlayerHeroPerformanceResponse> heroPerformance
    ) {
        Optional<BigDecimal> playerAverageKda = playerAverageKda(progress, heroPerformance);
        if (playerAverageKda.isEmpty()) {
            return;
        }

        heroPerformance.stream()
                .filter(hero -> hero.matches() >= MIN_HERO_SAMPLE_SIZE)
                .filter(hero -> hero.avgKda() != null)
                .map(hero -> new HeroInsightCandidate(hero, hero.avgKda().subtract(playerAverageKda.get())))
                .filter(candidate -> candidate.delta().compareTo(HERO_KDA_THRESHOLD) >= 0)
                .max(Comparator.comparing(HeroInsightCandidate::delta))
                .ifPresent(candidate -> insights.add(new PlayerInsightResponse(
                        "You perform better with " + heroName(candidate.hero()),
                        "Your average KDA on " + heroName(candidate.hero()) + " is "
                                + formatDecimal(candidate.hero().avgKda()) + ", above your filtered average of "
                                + formatDecimal(playerAverageKda.get()) + ".",
                        PlayerInsightCategory.POSITIVE,
                        "avgKda",
                        scaleMetric(candidate.hero().avgKda()),
                        scaleMetric(playerAverageKda.get()),
                        candidate.hero().matches(),
                        candidate.hero().matches() + " matches on " + heroName(candidate.hero()) + ".")));
    }

    private void addHeroWinRateInsight(
            List<PlayerInsightResponse> insights,
            List<PlayerHeroPerformanceResponse> heroPerformance
    ) {
        List<PlayerHeroPerformanceResponse> qualifiedHeroes = heroPerformance.stream()
                .filter(hero -> hero.matches() >= MIN_HERO_SAMPLE_SIZE)
                .filter(hero -> hero.winRate() != null)
                .sorted(Comparator.comparing(PlayerHeroPerformanceResponse::winRate))
                .toList();
        if (qualifiedHeroes.size() < 2) {
            return;
        }

        PlayerHeroPerformanceResponse weakerHero = qualifiedHeroes.get(0);
        PlayerHeroPerformanceResponse strongerHero = qualifiedHeroes.get(qualifiedHeroes.size() - 1);
        BigDecimal difference = strongerHero.winRate().subtract(weakerHero.winRate());
        if (difference.compareTo(HERO_WIN_RATE_THRESHOLD) < 0) {
            return;
        }

        insights.add(new PlayerInsightResponse(
                "Win rate is stronger on " + heroName(strongerHero),
                "Your win rate on " + heroName(strongerHero) + " is "
                        + formatDecimal(strongerHero.winRate()) + "%, compared with "
                        + formatDecimal(weakerHero.winRate()) + "% on " + heroName(weakerHero) + ".",
                PlayerInsightCategory.POSITIVE,
                "winRate",
                scaleMetric(strongerHero.winRate()),
                scaleMetric(weakerHero.winRate()),
                strongerHero.matches() + weakerHero.matches(),
                strongerHero.matches() + " matches on " + heroName(strongerHero) + " and "
                        + weakerHero.matches() + " matches on " + heroName(weakerHero) + "."));
    }

    private List<PlayerProgressPointResponse> chronologicalProgress(List<PlayerProgressPointResponse> progress) {
        return progress.stream()
                .sorted(Comparator.comparing(
                        PlayerProgressPointResponse::playedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    private List<PlayerProgressPointResponse> previousTrendWindow(List<PlayerProgressPointResponse> progress) {
        return progress.subList(progress.size() - MIN_TREND_SAMPLE_SIZE, progress.size() - TREND_WINDOW);
    }

    private List<PlayerProgressPointResponse> recentTrendWindow(List<PlayerProgressPointResponse> progress) {
        return progress.subList(progress.size() - TREND_WINDOW, progress.size());
    }

    private Optional<BigDecimal> playerAverageDeaths(
            List<PlayerProgressPointResponse> progress,
            List<PlayerHeroPerformanceResponse> heroPerformance
    ) {
        if (!progress.isEmpty()) {
            return averageDecimals(progress.stream()
                    .map(point -> BigDecimal.valueOf(point.deaths()))
                    .toList());
        }

        return weightedHeroAverage(heroPerformance, PlayerHeroPerformanceResponse::avgDeaths);
    }

    private Optional<BigDecimal> playerAverageKda(
            List<PlayerProgressPointResponse> progress,
            List<PlayerHeroPerformanceResponse> heroPerformance
    ) {
        Optional<BigDecimal> progressAverage = averageDecimals(progress.stream()
                .map(PlayerProgressPointResponse::kda)
                .toList());
        return progressAverage.isPresent()
                ? progressAverage
                : weightedHeroAverage(heroPerformance, PlayerHeroPerformanceResponse::avgKda);
    }

    private Optional<BigDecimal> weightedHeroAverage(
            List<PlayerHeroPerformanceResponse> heroPerformance,
            Function<PlayerHeroPerformanceResponse, BigDecimal> metric
    ) {
        BigDecimal weightedTotal = BigDecimal.ZERO;
        int sampleSize = 0;

        for (PlayerHeroPerformanceResponse hero : heroPerformance) {
            BigDecimal value = metric.apply(hero);
            if (value == null || hero.matches() <= 0) {
                continue;
            }
            weightedTotal = weightedTotal.add(value.multiply(BigDecimal.valueOf(hero.matches())));
            sampleSize += hero.matches();
        }

        if (sampleSize == 0) {
            return Optional.empty();
        }

        return Optional.of(weightedTotal.divide(BigDecimal.valueOf(sampleSize), 2, RoundingMode.HALF_UP));
    }

    private Optional<BigDecimal> averageDecimals(List<BigDecimal> values) {
        List<BigDecimal> usable = values.stream()
                .filter(Objects::nonNull)
                .toList();
        if (usable.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal total = usable.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Optional.of(total.divide(BigDecimal.valueOf(usable.size()), 2, RoundingMode.HALF_UP));
    }

    private Optional<BigDecimal> averageIntegers(List<Integer> values) {
        return averageDecimals(values.stream()
                .filter(Objects::nonNull)
                .map(BigDecimal::valueOf)
                .toList());
    }

    private BigDecimal scaleMetric(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String formatDecimal(BigDecimal value) {
        return scaleMetric(value)
                .stripTrailingZeros()
                .toPlainString();
    }

    private String heroName(PlayerHeroPerformanceResponse hero) {
        return hero.heroName() == null || hero.heroName().isBlank()
                ? "this hero"
                : hero.heroName();
    }

    private AuthenticatedActor requirePlayer() {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        if (actor.role() != ProfileRole.PLAYER) {
            throw new AccessDeniedException("Player profile role is required.");
        }

        return actor;
    }

    private AuthenticatedActor requireOrganizerOrAdmin() {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        if (actor.role() != ProfileRole.ORGANIZER && actor.role() != ProfileRole.ADMIN) {
            throw new AccessDeniedException("Organizer or admin profile role is required.");
        }

        return actor;
    }

    private record HeroInsightCandidate(PlayerHeroPerformanceResponse hero, BigDecimal delta) {
    }
}
