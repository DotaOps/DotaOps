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
import si.um.feri.dotaops.backend.analytics.domain.ContextWeightClassification;
import si.um.feri.dotaops.backend.analytics.domain.ContextWeightInput;
import si.um.feri.dotaops.backend.analytics.domain.ContextWeightResult;
import si.um.feri.dotaops.backend.analytics.repository.RoleBasedAnalyticsRepository;
import si.um.feri.dotaops.backend.analytics.web.CurrentTeamAnalyticsResponse;
import si.um.feri.dotaops.backend.analytics.web.OrganizerAnalyticsResponse;
import si.um.feri.dotaops.backend.analytics.web.OrganizerTournamentAnalyticsResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerAnalyticsResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerHeroPerformanceResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerInsightContextResponse;
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
    private final AnalyticsContextWeightingService contextWeightingService;

    public RoleBasedAnalyticsService(
            AnalyticsQueryService analyticsQueryService,
            RoleBasedAnalyticsRepository roleBasedAnalyticsRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TournamentRepository tournamentRepository,
            CurrentUserProvider currentUserProvider,
            AnalyticsContextWeightingService contextWeightingService
    ) {
        this.analyticsQueryService = analyticsQueryService;
        this.roleBasedAnalyticsRepository = roleBasedAnalyticsRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.tournamentRepository = tournamentRepository;
        this.currentUserProvider = currentUserProvider;
        this.contextWeightingService = contextWeightingService;
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
        List<WeightedProgressPoint> weightedProgress = weightedProgress(chronologicalProgress);

        addKdaTrendInsight(insights, weightedProgress);
        addEconomyTrendInsight(insights, weightedProgress);
        addContextWeightInsight(insights, weightedProgress);
        addHighDeathsHeroInsight(insights, chronologicalProgress, heroPerformance);
        addHeroKdaInsight(insights, chronologicalProgress, heroPerformance);
        addHeroWinRateInsight(insights, heroPerformance);

        return insights.stream()
                .limit(INSIGHT_LIMIT)
                .toList();
    }

    private void addKdaTrendInsight(
            List<PlayerInsightResponse> insights,
            List<WeightedProgressPoint> progress
    ) {
        if (progress.size() < MIN_TREND_SAMPLE_SIZE) {
            return;
        }

        List<WeightedProgressPoint> previous = previousTrendWindow(progress);
        List<WeightedProgressPoint> recent = recentTrendWindow(progress);
        Optional<BigDecimal> previousAverage = weightedAverageDecimals(previous, PlayerProgressPointResponse::kda);
        Optional<BigDecimal> recentAverage = weightedAverageDecimals(recent, PlayerProgressPointResponse::kda);
        if (previousAverage.isEmpty() || recentAverage.isEmpty()) {
            return;
        }

        BigDecimal improvement = recentAverage.get().subtract(previousAverage.get());
        if (improvement.compareTo(KDA_TREND_THRESHOLD) < 0) {
            return;
        }

        boolean contextAdjusted = hasAdjustedContext(previous) || hasAdjustedContext(recent);
        String averageLabel = contextAdjusted ? "context-weighted average KDA" : "average KDA";
        insights.add(new PlayerInsightResponse(
                "KDA trend is improving",
                "Your " + averageLabel + " over the last " + TREND_WINDOW + " matches is "
                        + formatDecimal(recentAverage.get()) + ", up from "
                        + formatDecimal(previousAverage.get()) + " in the previous " + TREND_WINDOW + ".",
                PlayerInsightCategory.POSITIVE,
                "KDA",
                scaleMetric(recentAverage.get()),
                scaleMetric(previousAverage.get()),
                MIN_TREND_SAMPLE_SIZE,
                trendEvidence(previous, recent),
                lowestAdjustedContext(previous, recent)
                        .map(PlayerInsightContextResponse::from)
                        .orElse(null)));
    }

    private void addEconomyTrendInsight(
            List<PlayerInsightResponse> insights,
            List<WeightedProgressPoint> progress
    ) {
        if (progress.size() < MIN_TREND_SAMPLE_SIZE) {
            return;
        }

        List<WeightedProgressPoint> previous = previousTrendWindow(progress);
        List<WeightedProgressPoint> recent = recentTrendWindow(progress);

        String metricName = null;
        BigDecimal currentValue = null;
        BigDecimal comparisonValue = null;
        BigDecimal largestDrop = BigDecimal.ZERO;

        Optional<BigDecimal> previousGpm = weightedAverageIntegers(previous, PlayerProgressPointResponse::goldPerMin);
        Optional<BigDecimal> recentGpm = weightedAverageIntegers(recent, PlayerProgressPointResponse::goldPerMin);
        if (previousGpm.isPresent() && recentGpm.isPresent()) {
            BigDecimal drop = previousGpm.get().subtract(recentGpm.get());
            if (drop.compareTo(ECONOMY_TREND_THRESHOLD) >= 0) {
                metricName = "GPM";
                currentValue = recentGpm.get();
                comparisonValue = previousGpm.get();
                largestDrop = drop;
            }
        }

        Optional<BigDecimal> previousXpm = weightedAverageIntegers(previous, PlayerProgressPointResponse::xpPerMin);
        Optional<BigDecimal> recentXpm = weightedAverageIntegers(recent, PlayerProgressPointResponse::xpPerMin);
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

        boolean contextAdjusted = hasAdjustedContext(previous) || hasAdjustedContext(recent);
        String averageLabel = contextAdjusted ? "context-weighted average " : "average ";
        insights.add(new PlayerInsightResponse(
                "Recent " + metricName + " trend is declining",
                "Your " + averageLabel + metricName + " over the last " + TREND_WINDOW + " matches is "
                        + formatDecimal(currentValue) + ", down from "
                        + formatDecimal(comparisonValue) + " in the previous " + TREND_WINDOW + ".",
                PlayerInsightCategory.WARNING,
                metricName,
                scaleMetric(currentValue),
                scaleMetric(comparisonValue),
                MIN_TREND_SAMPLE_SIZE,
                trendEvidence(previous, recent),
                lowestAdjustedContext(previous, recent)
                        .map(PlayerInsightContextResponse::from)
                        .orElse(null)));
    }

    private void addContextWeightInsight(
            List<PlayerInsightResponse> insights,
            List<WeightedProgressPoint> progress
    ) {
        List<WeightedProgressPoint> adjusted = progress.stream()
                .filter(point -> point.contextWeight().isAdjusted())
                .toList();
        if (adjusted.isEmpty()) {
            return;
        }

        ContextWeightResult lowestWeight = adjusted.stream()
                .map(WeightedProgressPoint::contextWeight)
                .min(Comparator.comparing(ContextWeightResult::weight))
                .orElseThrow();
        long stompLosses = adjusted.stream()
                .filter(point -> point.contextWeight().classification() == ContextWeightClassification.STOMP_LOSS)
                .count();

        String roughSummary = stompLosses > 0
                ? stompLosses + " stomp-like loss(es) and " + adjusted.size() + " rough match(es)"
                : adjusted.size() + " rough match(es)";
        insights.add(new PlayerInsightResponse(
                "Rough games are context-weighted",
                "Detected " + roughSummary
                        + ". Raw match values stay unchanged; trend insights reduce their influence.",
                PlayerInsightCategory.INFO,
                "contextWeight",
                lowestWeight.weight(),
                BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP),
                adjusted.size(),
                "Lowest context weight was " + formatDecimal(lowestWeight.weight())
                        + " across " + progress.size() + " filtered match(es).",
                PlayerInsightContextResponse.from(lowestWeight)));
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

    private List<WeightedProgressPoint> weightedProgress(List<PlayerProgressPointResponse> progress) {
        return progress.stream()
                .map(point -> new WeightedProgressPoint(
                        point,
                        contextWeightingService.evaluate(contextWeightInput(point))))
                .toList();
    }

    private ContextWeightInput contextWeightInput(PlayerProgressPointResponse point) {
        return new ContextWeightInput(
                point.kills(),
                point.deaths(),
                point.assists(),
                point.kda(),
                point.goldPerMin(),
                point.xpPerMin(),
                point.heroDamage(),
                point.towerDamage(),
                point.heroHealing(),
                point.lastHits(),
                point.denies(),
                point.netWorth(),
                point.level(),
                point.won(),
                point.teamSide(),
                point.radiantScore(),
                point.direScore());
    }

    private <T> List<T> previousTrendWindow(List<T> progress) {
        return progress.subList(progress.size() - MIN_TREND_SAMPLE_SIZE, progress.size() - TREND_WINDOW);
    }

    private <T> List<T> recentTrendWindow(List<T> progress) {
        return progress.subList(progress.size() - TREND_WINDOW, progress.size());
    }

    private boolean hasAdjustedContext(List<WeightedProgressPoint> progress) {
        return progress.stream()
                .anyMatch(point -> point.contextWeight().isAdjusted());
    }

    private String trendEvidence(
            List<WeightedProgressPoint> previous,
            List<WeightedProgressPoint> recent
    ) {
        String evidence = "Compared " + TREND_WINDOW + " recent matches against the previous " + TREND_WINDOW + ".";
        if (!hasAdjustedContext(previous) && !hasAdjustedContext(recent)) {
            return evidence;
        }

        return evidence + " Context weights reduce rough-game influence while raw match history stays unchanged.";
    }

    private Optional<ContextWeightResult> lowestAdjustedContext(
            List<WeightedProgressPoint> previous,
            List<WeightedProgressPoint> recent
    ) {
        List<WeightedProgressPoint> combined = new ArrayList<>(previous);
        combined.addAll(recent);
        return combined.stream()
                .map(WeightedProgressPoint::contextWeight)
                .filter(ContextWeightResult::isAdjusted)
                .min(Comparator.comparing(ContextWeightResult::weight));
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

    private Optional<BigDecimal> weightedAverageDecimals(
            List<WeightedProgressPoint> values,
            Function<PlayerProgressPointResponse, BigDecimal> metric
    ) {
        BigDecimal weightedTotal = BigDecimal.ZERO;
        BigDecimal weightTotal = BigDecimal.ZERO;

        for (WeightedProgressPoint value : values) {
            BigDecimal metricValue = metric.apply(value.point());
            if (metricValue == null) {
                continue;
            }
            BigDecimal weight = value.contextWeight().weight();
            weightedTotal = weightedTotal.add(metricValue.multiply(weight));
            weightTotal = weightTotal.add(weight);
        }

        if (weightTotal.compareTo(BigDecimal.ZERO) == 0) {
            return Optional.empty();
        }

        return Optional.of(weightedTotal.divide(weightTotal, 2, RoundingMode.HALF_UP));
    }

    private Optional<BigDecimal> weightedAverageIntegers(
            List<WeightedProgressPoint> values,
            Function<PlayerProgressPointResponse, Integer> metric
    ) {
        return weightedAverageDecimals(values, point -> {
            Integer value = metric.apply(point);
            return value == null ? null : BigDecimal.valueOf(value);
        });
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

    private record WeightedProgressPoint(PlayerProgressPointResponse point, ContextWeightResult contextWeight) {
    }
}
