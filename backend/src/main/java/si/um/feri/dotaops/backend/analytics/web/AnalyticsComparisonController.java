package si.um.feri.dotaops.backend.analytics.web;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import si.um.feri.dotaops.backend.analytics.domain.AnalyticsFilters;
import si.um.feri.dotaops.backend.analytics.service.AnalyticsComparisonService;
import si.um.feri.dotaops.backend.common.api.ApiResponse;

@Validated
@RestController
@RequestMapping("/api/analytics/compare")
public class AnalyticsComparisonController {

    private final AnalyticsComparisonService analyticsComparisonService;

    public AnalyticsComparisonController(AnalyticsComparisonService analyticsComparisonService) {
        this.analyticsComparisonService = analyticsComparisonService;
    }

    @GetMapping("/teams")
    ApiResponse<TeamComparisonResponse> compareTeams(
            @RequestParam @NotNull UUID teamAId,
            @RequestParam @NotNull UUID teamBId,
            @RequestParam(required = false) UUID tournamentId,
            @RequestParam(name = "tournament_id", required = false) UUID tournamentIdSnake,
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
        return ApiResponse.of(analyticsComparisonService.compareTeams(
                teamAId,
                teamBId,
                new AnalyticsFilters(
                        firstNonNull(tournamentId, tournamentIdSnake),
                        null,
                        firstNonNull(profileId, profileIdSnake),
                        firstNonNull(heroId, heroIdSnake),
                        from,
                        to,
                        limit)));
    }

    @GetMapping("/players")
    ApiResponse<PlayerComparisonResponse> comparePlayers(
            @RequestParam @NotNull UUID profileAId,
            @RequestParam @NotNull UUID profileBId,
            @RequestParam(required = false) UUID tournamentId,
            @RequestParam(name = "tournament_id", required = false) UUID tournamentIdSnake,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(name = "team_id", required = false) UUID teamIdSnake,
            @RequestParam(required = false) UUID heroId,
            @RequestParam(name = "hero_id", required = false) UUID heroIdSnake,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit
    ) {
        return ApiResponse.of(analyticsComparisonService.comparePlayers(
                profileAId,
                profileBId,
                new AnalyticsFilters(
                        firstNonNull(tournamentId, tournamentIdSnake),
                        firstNonNull(teamId, teamIdSnake),
                        null,
                        firstNonNull(heroId, heroIdSnake),
                        from,
                        to,
                        limit)));
    }

    private UUID firstNonNull(UUID primary, UUID secondary) {
        return primary == null ? secondary : primary;
    }
}
