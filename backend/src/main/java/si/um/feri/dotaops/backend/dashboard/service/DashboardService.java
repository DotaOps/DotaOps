package si.um.feri.dotaops.backend.dashboard.service;

import java.util.Locale;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedProfile;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.auth.service.SupabasePrincipal;
import si.um.feri.dotaops.backend.dashboard.repository.DashboardRepository;
import si.um.feri.dotaops.backend.dashboard.web.AdminDashboardResponse;
import si.um.feri.dotaops.backend.dashboard.web.DashboardCapabilitiesResponse;
import si.um.feri.dotaops.backend.dashboard.web.MeDashboardResponse;
import si.um.feri.dotaops.backend.dashboard.web.OrganizerDashboardResponse;
import si.um.feri.dotaops.backend.dashboard.web.PlayerDashboardResponse;
import si.um.feri.dotaops.backend.team.service.TeamRosterService;
import si.um.feri.dotaops.backend.team.web.CurrentTeamResponse;

@Service
public class DashboardService {

    private final DashboardRepository dashboardRepository;
    private final TeamRosterService teamRosterService;
    private final CurrentUserProvider currentUserProvider;

    public DashboardService(
            DashboardRepository dashboardRepository,
            TeamRosterService teamRosterService,
            CurrentUserProvider currentUserProvider
    ) {
        this.dashboardRepository = dashboardRepository;
        this.teamRosterService = teamRosterService;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public MeDashboardResponse getCurrentUserDashboard() {
        AuthenticatedProfile profile = currentUserProvider.requireProfile();

        return switch (profile.role()) {
            case PLAYER -> playerDashboard(profile);
            case ORGANIZER -> organizerDashboard(profile);
            case ADMIN -> adminDashboard(profile);
            case VISITOR -> throw new AccessDeniedException("A persisted DotaOps profile role is required.");
        };
    }

    private MeDashboardResponse playerDashboard(AuthenticatedProfile profile) {
        CurrentTeamResponse currentTeam = teamRosterService.getCurrentTeam();
        long pendingInvitations = dashboardRepository.countPendingInvitations(
                profile.profileId(),
                normalizedCurrentUserEmail());
        long tournamentRegistrations = currentTeam.team() == null
                ? 0
                : dashboardRepository.countTournamentRegistrations(currentTeam.team().id());

        return new MeDashboardResponse(
                profile.role().databaseValue(),
                playerCapabilities(currentTeam),
                new PlayerDashboardResponse(currentTeam, pendingInvitations, tournamentRegistrations),
                null,
                null);
    }

    private MeDashboardResponse organizerDashboard(AuthenticatedProfile profile) {
        var counts = dashboardRepository.findOrganizerCounts(profile.profileId(), false);

        return new MeDashboardResponse(
                profile.role().databaseValue(),
                organizerCapabilities(),
                null,
                new OrganizerDashboardResponse(
                        counts.tournaments(),
                        counts.pendingRegistrations(),
                        counts.activePublishedTournaments(),
                        counts.processedMatchGames(),
                        counts.importJobs()),
                null);
    }

    private MeDashboardResponse adminDashboard(AuthenticatedProfile profile) {
        var counts = dashboardRepository.findAdminCounts();

        return new MeDashboardResponse(
                profile.role().databaseValue(),
                adminCapabilities(),
                null,
                null,
                new AdminDashboardResponse(
                        counts.profiles(),
                        counts.tournaments(),
                        counts.pendingRegistrations(),
                        counts.importJobs()));
    }

    private DashboardCapabilitiesResponse playerCapabilities(CurrentTeamResponse currentTeam) {
        return new DashboardCapabilitiesResponse(
                currentTeam.canCreateTeam(),
                currentTeam.isTeamOwner(),
                currentTeam.currentUserTeamRole(),
                currentTeam.canManageTeam(),
                currentTeam.canManageRoster(),
                currentTeam.canInvitePlayers(),
                currentTeam.canTransferOwnership(),
                true,
                false,
                false);
    }

    private DashboardCapabilitiesResponse organizerCapabilities() {
        return new DashboardCapabilitiesResponse(
                false,
                false,
                null,
                false,
                false,
                false,
                false,
                true,
                true,
                true);
    }

    private DashboardCapabilitiesResponse adminCapabilities() {
        return new DashboardCapabilitiesResponse(
                false,
                false,
                null,
                false,
                false,
                false,
                false,
                true,
                true,
                true);
    }

    private String normalizedCurrentUserEmail() {
        return currentUserProvider.currentUser()
                .map(SupabasePrincipal::email)
                .filter(email -> !email.isBlank())
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .orElse(null);
    }
}
