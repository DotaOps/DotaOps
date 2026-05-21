package si.um.feri.dotaops.backend.analytics.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import si.um.feri.dotaops.backend.analytics.service.AnalyticsRefreshService;
import si.um.feri.dotaops.backend.common.api.ApiResponse;

@RestController
@RequestMapping("/api/admin/analytics")
public class AdminAnalyticsController {

    private final AnalyticsRefreshService analyticsRefreshService;

    public AdminAnalyticsController(AnalyticsRefreshService analyticsRefreshService) {
        this.analyticsRefreshService = analyticsRefreshService;
    }

    @PostMapping("/refresh")
    ApiResponse<AnalyticsRefreshResponse> refreshAnalytics() {
        return ApiResponse.of(analyticsRefreshService.refreshNow("admin request"));
    }
}
