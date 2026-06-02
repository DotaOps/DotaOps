package si.um.feri.dotaops.backend.analytics.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import si.um.feri.dotaops.backend.analytics.service.RoleBasedAnalyticsService;
import si.um.feri.dotaops.backend.common.api.ApiResponse;

@RestController
@RequestMapping("/api/me")
public class MeAnalyticsController {

    private final RoleBasedAnalyticsService roleBasedAnalyticsService;

    public MeAnalyticsController(RoleBasedAnalyticsService roleBasedAnalyticsService) {
        this.roleBasedAnalyticsService = roleBasedAnalyticsService;
    }

    @GetMapping("/analytics")
    ApiResponse<PlayerAnalyticsResponse> currentPlayerAnalytics() {
        return ApiResponse.of(roleBasedAnalyticsService.currentPlayerAnalytics());
    }

    @GetMapping("/team/analytics")
    ApiResponse<CurrentTeamAnalyticsResponse> currentTeamAnalytics() {
        return ApiResponse.of(roleBasedAnalyticsService.currentTeamAnalytics());
    }
}
