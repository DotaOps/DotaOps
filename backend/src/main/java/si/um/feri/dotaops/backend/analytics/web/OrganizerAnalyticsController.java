package si.um.feri.dotaops.backend.analytics.web;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import si.um.feri.dotaops.backend.analytics.service.RoleBasedAnalyticsService;
import si.um.feri.dotaops.backend.common.api.ApiResponse;

@RestController
@RequestMapping("/api/organizer")
public class OrganizerAnalyticsController {

    private final RoleBasedAnalyticsService roleBasedAnalyticsService;

    public OrganizerAnalyticsController(RoleBasedAnalyticsService roleBasedAnalyticsService) {
        this.roleBasedAnalyticsService = roleBasedAnalyticsService;
    }

    @GetMapping("/analytics")
    ApiResponse<OrganizerAnalyticsResponse> organizerAnalytics() {
        return ApiResponse.of(roleBasedAnalyticsService.organizerAnalytics());
    }

    @GetMapping("/tournaments/{tournamentId}/analytics")
    ApiResponse<OrganizerTournamentAnalyticsResponse> organizerTournamentAnalytics(
            @PathVariable UUID tournamentId
    ) {
        return ApiResponse.of(roleBasedAnalyticsService.organizerTournamentAnalytics(tournamentId));
    }
}
