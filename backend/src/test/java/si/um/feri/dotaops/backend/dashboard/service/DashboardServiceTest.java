package si.um.feri.dotaops.backend.dashboard.service;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedProfile;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.auth.service.SupabasePrincipal;
import si.um.feri.dotaops.backend.dashboard.repository.DashboardRepository;
import si.um.feri.dotaops.backend.team.service.TeamRosterService;
import si.um.feri.dotaops.backend.team.web.CurrentTeamResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private final DashboardRepository dashboardRepository = mock(DashboardRepository.class);
    private final TeamRosterService teamRosterService = mock(TeamRosterService.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final DashboardService dashboardService = new DashboardService(
            dashboardRepository,
            teamRosterService,
            currentUserProvider);

    @Test
    void playerDashboardReturnsRealEmptyStateAndCapabilities() {
        when(currentUserProvider.requireProfile()).thenReturn(profile(ProfileRole.PLAYER));
        when(currentUserProvider.currentUser()).thenReturn(Optional.of(principal(ProfileRole.PLAYER)));
        when(teamRosterService.getCurrentTeam()).thenReturn(CurrentTeamResponse.none(true));
        when(dashboardRepository.countPendingInvitations(PROFILE_ID, "player@example.test")).thenReturn(2L);

        var response = dashboardService.getCurrentUserDashboard();

        assertThat(response.role()).isEqualTo("player");
        assertThat(response.capabilities().canCreateTeam()).isTrue();
        assertThat(response.capabilities().canManageTeam()).isFalse();
        assertThat(response.capabilities().canViewAnalytics()).isTrue();
        assertThat(response.player().pendingInvitations()).isEqualTo(2);
        assertThat(response.player().tournamentRegistrations()).isZero();
        assertThat(response.player().currentTeam().team()).isNull();
        assertThat(response.organizer()).isNull();
        assertThat(response.admin()).isNull();
    }

    @Test
    void organizerDashboardReturnsOnlyOrganizerSummary() {
        when(currentUserProvider.requireProfile()).thenReturn(profile(ProfileRole.ORGANIZER));
        when(dashboardRepository.findOrganizerCounts(PROFILE_ID, false))
                .thenReturn(new DashboardRepository.OrganizerDashboardCounts(3, 4, 2, 5, 6));

        var response = dashboardService.getCurrentUserDashboard();

        assertThat(response.role()).isEqualTo("organizer");
        assertThat(response.capabilities().canCreateTeam()).isFalse();
        assertThat(response.capabilities().canManageTournament()).isTrue();
        assertThat(response.capabilities().canViewOrganizerDashboard()).isTrue();
        assertThat(response.organizer().tournaments()).isEqualTo(3);
        assertThat(response.organizer().pendingRegistrations()).isEqualTo(4);
        assertThat(response.player()).isNull();
        assertThat(response.admin()).isNull();
    }

    @Test
    void adminDashboardReturnsSystemCountsWithoutImplicitTeamCreation() {
        when(currentUserProvider.requireProfile()).thenReturn(profile(ProfileRole.ADMIN));
        when(dashboardRepository.findAdminCounts())
                .thenReturn(new DashboardRepository.AdminDashboardCounts(20, 7, 4, 9));

        var response = dashboardService.getCurrentUserDashboard();

        assertThat(response.role()).isEqualTo("admin");
        assertThat(response.capabilities().canCreateTeam()).isFalse();
        assertThat(response.admin().profiles()).isEqualTo(20);
        assertThat(response.admin().tournaments()).isEqualTo(7);
        assertThat(response.player()).isNull();
        assertThat(response.organizer()).isNull();
    }

    private AuthenticatedProfile profile(ProfileRole role) {
        return new AuthenticatedProfile(PROFILE_ID, AUTH_USER_ID, "Profile", role);
    }

    private SupabasePrincipal principal(ProfileRole role) {
        return new SupabasePrincipal(
                AUTH_USER_ID,
                "Player@Example.test",
                Optional.of(profile(role)),
                null);
    }
}
