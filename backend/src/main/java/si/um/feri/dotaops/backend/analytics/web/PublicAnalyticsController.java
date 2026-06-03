package si.um.feri.dotaops.backend.analytics.web;

import java.util.List;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import si.um.feri.dotaops.backend.analytics.domain.AnalyticsFilters;
import si.um.feri.dotaops.backend.analytics.service.AnalyticsQueryService;
import si.um.feri.dotaops.backend.common.api.ApiResponse;

@Validated
@RestController
@RequestMapping("/api/public/analytics")
public class PublicAnalyticsController {

    private final AnalyticsQueryService analyticsQueryService;

    public PublicAnalyticsController(AnalyticsQueryService analyticsQueryService) {
        this.analyticsQueryService = analyticsQueryService;
    }

    @GetMapping("/players")
    ApiResponse<List<PlayerMetricsResponse>> playerMetrics(
            @RequestParam(required = false) UUID tournamentId,
            @RequestParam(name = "tournament_id", required = false) UUID tournamentIdSnake,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(name = "team_id", required = false) UUID teamIdSnake,
            @RequestParam(required = false) UUID profileId,
            @RequestParam(name = "profile_id", required = false) UUID profileIdSnake,
            @RequestParam(required = false) UUID heroId,
            @RequestParam(name = "hero_id", required = false) UUID heroIdSnake,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit
    ) {
        return ApiResponse.of(analyticsQueryService.playerMetrics(filters(
                tournamentId,
                tournamentIdSnake,
                teamId,
                teamIdSnake,
                profileId,
                profileIdSnake,
                heroId,
                heroIdSnake,
                from,
                to,
                limit)));
    }

    @GetMapping("/teams")
    ApiResponse<List<TeamMetricsResponse>> teamMetrics(
            @RequestParam(required = false) UUID tournamentId,
            @RequestParam(name = "tournament_id", required = false) UUID tournamentIdSnake,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(name = "team_id", required = false) UUID teamIdSnake,
            @RequestParam(required = false) UUID profileId,
            @RequestParam(name = "profile_id", required = false) UUID profileIdSnake,
            @RequestParam(required = false) UUID heroId,
            @RequestParam(name = "hero_id", required = false) UUID heroIdSnake,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit
    ) {
        return ApiResponse.of(analyticsQueryService.teamMetrics(filters(
                tournamentId,
                tournamentIdSnake,
                teamId,
                teamIdSnake,
                profileId,
                profileIdSnake,
                heroId,
                heroIdSnake,
                from,
                to,
                limit)));
    }

    @GetMapping("/heroes")
    ApiResponse<List<HeroMetricsResponse>> heroMetrics(
            @RequestParam(required = false) UUID tournamentId,
            @RequestParam(name = "tournament_id", required = false) UUID tournamentIdSnake,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(name = "team_id", required = false) UUID teamIdSnake,
            @RequestParam(required = false) UUID profileId,
            @RequestParam(name = "profile_id", required = false) UUID profileIdSnake,
            @RequestParam(required = false) UUID heroId,
            @RequestParam(name = "hero_id", required = false) UUID heroIdSnake,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit
    ) {
        return ApiResponse.of(analyticsQueryService.heroMetrics(filters(
                tournamentId,
                tournamentIdSnake,
                teamId,
                teamIdSnake,
                profileId,
                profileIdSnake,
                heroId,
                heroIdSnake,
                from,
                to,
                limit)));
    }

    @GetMapping("/tournaments")
    ApiResponse<List<TournamentMetricsResponse>> tournamentMetrics(
            @RequestParam(required = false) UUID tournamentId,
            @RequestParam(name = "tournament_id", required = false) UUID tournamentIdSnake,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(name = "team_id", required = false) UUID teamIdSnake,
            @RequestParam(required = false) UUID profileId,
            @RequestParam(name = "profile_id", required = false) UUID profileIdSnake,
            @RequestParam(required = false) UUID heroId,
            @RequestParam(name = "hero_id", required = false) UUID heroIdSnake,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit
    ) {
        return ApiResponse.of(analyticsQueryService.tournamentMetrics(filters(
                tournamentId,
                tournamentIdSnake,
                teamId,
                teamIdSnake,
                profileId,
                profileIdSnake,
                heroId,
                heroIdSnake,
                from,
                to,
                limit)));
    }

    @GetMapping("/tournaments/{tournamentId}")
    ApiResponse<TournamentMetricsResponse> tournamentMetricsById(@PathVariable UUID tournamentId) {
        return ApiResponse.of(analyticsQueryService.tournamentMetrics(tournamentId));
    }

    private AnalyticsFilters filters(
            UUID tournamentId,
            UUID tournamentIdSnake,
            UUID teamId,
            UUID teamIdSnake,
            UUID profileId,
            UUID profileIdSnake,
            UUID heroId,
            UUID heroIdSnake,
            OffsetDateTime from,
            OffsetDateTime to,
            int limit
    ) {
        return new AnalyticsFilters(
                firstNonNull(tournamentId, tournamentIdSnake),
                firstNonNull(teamId, teamIdSnake),
                firstNonNull(profileId, profileIdSnake),
                firstNonNull(heroId, heroIdSnake),
                from,
                to,
                limit);
    }

    private UUID firstNonNull(UUID primary, UUID secondary) {
        return primary == null ? secondary : primary;
    }
}
