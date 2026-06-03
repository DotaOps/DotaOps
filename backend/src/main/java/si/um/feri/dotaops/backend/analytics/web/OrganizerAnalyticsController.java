package si.um.feri.dotaops.backend.analytics.web;

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
import si.um.feri.dotaops.backend.analytics.service.RoleBasedAnalyticsService;
import si.um.feri.dotaops.backend.common.api.ApiResponse;

@Validated
@RestController
@RequestMapping("/api/organizer")
public class OrganizerAnalyticsController {

    private final RoleBasedAnalyticsService roleBasedAnalyticsService;

    public OrganizerAnalyticsController(RoleBasedAnalyticsService roleBasedAnalyticsService) {
        this.roleBasedAnalyticsService = roleBasedAnalyticsService;
    }

    @GetMapping("/analytics")
    ApiResponse<OrganizerAnalyticsResponse> organizerAnalytics(
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
        return ApiResponse.of(roleBasedAnalyticsService.organizerAnalytics(filters(
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

    @GetMapping("/tournaments/{tournamentId}/analytics")
    ApiResponse<OrganizerTournamentAnalyticsResponse> organizerTournamentAnalytics(
            @PathVariable UUID tournamentId,
            @RequestParam(required = false) UUID queryTournamentId,
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
        return ApiResponse.of(roleBasedAnalyticsService.organizerTournamentAnalytics(tournamentId, filters(
                queryTournamentId,
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
