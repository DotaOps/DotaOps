package si.um.feri.dotaops.backend.analytics.service;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.um.feri.dotaops.backend.analytics.domain.AnalyticsFilters;
import si.um.feri.dotaops.backend.analytics.repository.RoleBasedAnalyticsRepository;
import si.um.feri.dotaops.backend.analytics.web.CurrentTeamAnalyticsResponse;
import si.um.feri.dotaops.backend.analytics.web.OrganizerAnalyticsResponse;
import si.um.feri.dotaops.backend.analytics.web.OrganizerTournamentAnalyticsResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerAnalyticsResponse;
import si.um.feri.dotaops.backend.analytics.web.RecentImportResponse;
import si.um.feri.dotaops.backend.auth.domain.AuthenticatedActor;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.ResourceNotFoundException;
import si.um.feri.dotaops.backend.team.repository.TeamRepository;
import si.um.feri.dotaops.backend.team.web.TeamResponse;
import si.um.feri.dotaops.backend.tournament.repository.TournamentRepository;

@Service
public class RoleBasedAnalyticsService {

    private static final int MAX_METRICS = 100;
    private static final int RECENT_IMPORTS = 10;

    private final AnalyticsQueryService analyticsQueryService;
    private final RoleBasedAnalyticsRepository roleBasedAnalyticsRepository;
    private final TeamRepository teamRepository;
    private final TournamentRepository tournamentRepository;
    private final CurrentUserProvider currentUserProvider;

    public RoleBasedAnalyticsService(
            AnalyticsQueryService analyticsQueryService,
            RoleBasedAnalyticsRepository roleBasedAnalyticsRepository,
            TeamRepository teamRepository,
            TournamentRepository tournamentRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.analyticsQueryService = analyticsQueryService;
        this.roleBasedAnalyticsRepository = roleBasedAnalyticsRepository;
        this.teamRepository = teamRepository;
        this.tournamentRepository = tournamentRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public PlayerAnalyticsResponse currentPlayerAnalytics() {
        AuthenticatedActor actor = requirePlayer();
        AnalyticsFilters filters = new AnalyticsFilters(null, null, actor.requireProfileId(), null, MAX_METRICS);

        return new PlayerAnalyticsResponse(
                analyticsQueryService.protectedPlayerMetrics(filters),
                analyticsQueryService.protectedHeroMetrics(filters),
                List.of());
    }

    @Transactional(readOnly = true)
    public CurrentTeamAnalyticsResponse currentTeamAnalytics() {
        AuthenticatedActor actor = requirePlayer();

        return teamRepository.findCurrentTeamForProfile(actor.requireProfileId())
                .map(team -> {
                    AnalyticsFilters filters = new AnalyticsFilters(null, team.id(), null, null, MAX_METRICS);
                    return new CurrentTeamAnalyticsResponse(
                            TeamResponse.from(team),
                            analyticsQueryService.protectedTeamMetrics(filters),
                            analyticsQueryService.protectedPlayerMetrics(filters),
                            List.of());
                })
                .orElseGet(() -> new CurrentTeamAnalyticsResponse(null, List.of(), List.of(), List.of()));
    }

    @Transactional(readOnly = true)
    public OrganizerAnalyticsResponse organizerAnalytics() {
        AuthenticatedActor actor = requireOrganizerOrAdmin();
        var counts = roleBasedAnalyticsRepository.findOrganizerCounts(actor.requireProfileId(), actor.isAdmin());

        return new OrganizerAnalyticsResponse(
                counts.tournaments(),
                counts.pendingRegistrations(),
                counts.approvedRegistrations(),
                counts.activePublishedTournaments(),
                counts.processedMatchGames(),
                counts.importJobs());
    }

    @Transactional(readOnly = true)
    public OrganizerTournamentAnalyticsResponse organizerTournamentAnalytics(UUID tournamentId) {
        AuthenticatedActor actor = requireOrganizerOrAdmin();
        tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));
        if (!tournamentRepository.canManage(tournamentId, actor.requireProfileId(), actor.isAdmin())) {
            throw new AccessDeniedException("Only tournament organizers can view private tournament analytics.");
        }

        AnalyticsFilters filters = new AnalyticsFilters(tournamentId, null, null, null, MAX_METRICS);
        var operations = roleBasedAnalyticsRepository.findTournamentOperationalMetrics(tournamentId);
        var teamMetrics = analyticsQueryService.protectedTeamMetrics(filters);

        return new OrganizerTournamentAnalyticsResponse(
                tournamentId,
                operations.gamesProcessed(),
                operations.matchesWithoutImport(),
                operations.importCoveragePercent(),
                operations.avgDurationSeconds(),
                analyticsQueryService.protectedTournamentMetrics(tournamentId).orElse(null),
                teamMetrics,
                analyticsQueryService.protectedHeroMetrics(filters),
                teamMetrics,
                roleBasedAnalyticsRepository.findRecentImports(tournamentId, RECENT_IMPORTS)
                        .stream()
                        .map(RecentImportResponse::from)
                        .toList());
    }

    private AuthenticatedActor requirePlayer() {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        if (actor.role() != ProfileRole.PLAYER) {
            throw new AccessDeniedException("Player profile role is required.");
        }

        return actor;
    }

    private AuthenticatedActor requireOrganizerOrAdmin() {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        if (actor.role() != ProfileRole.ORGANIZER && actor.role() != ProfileRole.ADMIN) {
            throw new AccessDeniedException("Organizer or admin profile role is required.");
        }

        return actor;
    }
}
