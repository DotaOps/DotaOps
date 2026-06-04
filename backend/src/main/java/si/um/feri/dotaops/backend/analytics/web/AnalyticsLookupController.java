package si.um.feri.dotaops.backend.analytics.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import si.um.feri.dotaops.backend.analytics.service.AnalyticsLookupService;
import si.um.feri.dotaops.backend.common.api.ApiResponse;

@Validated
@RestController
@RequestMapping("/api")
public class AnalyticsLookupController {

    private final AnalyticsLookupService analyticsLookupService;

    public AnalyticsLookupController(AnalyticsLookupService analyticsLookupService) {
        this.analyticsLookupService = analyticsLookupService;
    }

    @GetMapping("/organizer/lookups/tournaments")
    ApiResponse<List<AnalyticsTournamentLookupResponse>> organizerTournaments(
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit
    ) {
        return ApiResponse.of(analyticsLookupService.organizerTournaments(limit));
    }

    @GetMapping("/me/lookups/teams")
    ApiResponse<List<AnalyticsTeamLookupResponse>> currentPlayerTeams(
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit
    ) {
        return ApiResponse.of(analyticsLookupService.currentPlayerTeams(limit));
    }

    @GetMapping("/teams/{teamId}/lookups/players")
    ApiResponse<List<AnalyticsPlayerLookupResponse>> teamPlayers(
            @PathVariable UUID teamId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit
    ) {
        return ApiResponse.of(analyticsLookupService.teamPlayers(teamId, limit));
    }

    @GetMapping("/lookups/heroes")
    ApiResponse<List<AnalyticsHeroLookupResponse>> heroes(
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit
    ) {
        return ApiResponse.of(analyticsLookupService.heroes(limit));
    }
}
