package si.um.feri.dotaops.backend.integration;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import si.um.feri.dotaops.backend.analytics.domain.AnalyticsFilters;
import si.um.feri.dotaops.backend.analytics.repository.AnalyticsRepository;
import si.um.feri.dotaops.backend.analytics.repository.RoleBasedAnalyticsRepository;
import si.um.feri.dotaops.backend.dashboard.repository.DashboardRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
@EnabledIfEnvironmentVariable(named = "SUPABASE_DB_URL", matches = ".+")
class DashboardAnalyticsRepositoryIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    private DashboardRepository dashboardRepository;

    @Autowired
    private AnalyticsRepository analyticsRepository;

    @Autowired
    private RoleBasedAnalyticsRepository roleBasedAnalyticsRepository;

    @Test
    void dashboardAndProtectedAnalyticsQueriesExecuteAgainstPostgres() {
        UUID playerAuthUserId = UUID.randomUUID();
        UUID organizerAuthUserId = UUID.randomUUID();
        UUID playerProfileId = upsertProfile(playerAuthUserId, "player");
        UUID organizerProfileId = upsertProfile(organizerAuthUserId, "organizer");
        UUID missingTeamId = UUID.randomUUID();
        UUID missingTournamentId = UUID.randomUUID();

        assertThat(dashboardRepository.countPendingInvitations(playerProfileId, null)).isZero();
        assertThat(dashboardRepository.countTournamentRegistrations(missingTeamId)).isZero();
        assertThat(dashboardRepository.findOrganizerCounts(organizerProfileId, false).tournaments()).isZero();
        assertThat(dashboardRepository.findAdminCounts().profiles()).isGreaterThanOrEqualTo(2);

        AnalyticsFilters playerFilters = new AnalyticsFilters(null, null, playerProfileId, null, 10);
        assertThat(analyticsRepository.findProtectedPlayerMetrics(playerFilters)).isEmpty();
        assertThat(analyticsRepository.findProtectedHeroMetrics(playerFilters)).isEmpty();
        assertThat(analyticsRepository.findProtectedTeamMetrics(
                new AnalyticsFilters(null, missingTeamId, null, null, 10))).isEmpty();
        assertThat(analyticsRepository.findProtectedTournamentMetricsById(missingTournamentId)).isEmpty();

        assertThat(roleBasedAnalyticsRepository.findOrganizerCounts(organizerProfileId, false).tournaments()).isZero();
        assertThat(roleBasedAnalyticsRepository.findTournamentOperationalMetrics(missingTournamentId).gamesProcessed())
                .isZero();
        assertThat(roleBasedAnalyticsRepository.findRecentImports(missingTournamentId, 10)).isEmpty();
    }
}
