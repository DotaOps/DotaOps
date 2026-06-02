package si.um.feri.dotaops.backend.dashboard.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import si.um.feri.dotaops.backend.common.api.ApiResponse;
import si.um.feri.dotaops.backend.dashboard.service.DashboardService;

@RestController
@RequestMapping("/api/me/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    ApiResponse<MeDashboardResponse> getCurrentUserDashboard() {
        return ApiResponse.of(dashboardService.getCurrentUserDashboard());
    }
}
