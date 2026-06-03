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
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.ResourceNotFoundException;
import si.um.feri.dotaops.backend.team.domain.Team;
import si.um.feri.dotaops.backend.team.repository.TeamMemberRepository;
import si.um.feri.dotaops.backend.team.repository.TeamRepository;
import si.um.feri.dotaops.backend.team.web.TeamResponse;
import si.um.feri.dotaops.backend.tournament.repository.TournamentRepository;

@Service
public class RoleBasedAnalyticsService {

    private static final int RECENT_IMPORTS = 10;

    private final AnalyticsQueryService analyticsQueryService;
    private final RoleBasedAnalyticsRepository roleBasedAnalyticsRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TournamentRepository tournamentRepository;
    private final CurrentUserProvider currentUserProvider;

    public RoleBasedAnalyticsService(
            AnalyticsQueryService analyticsQueryService,
            RoleBasedAnalyticsRepository roleBasedAnalyticsRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TournamentRepository tournamentRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.analyticsQueryService = analyticsQueryService;
        this.roleBasedAnalyticsRepository = roleBasedAnalyticsRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.tournamentRepository = tournamentRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public PlayerAnalyticsResponse currentPlayerAnalytics() {
        return currentPlayerAnalytics(new AnalyticsFilters(null, null, null, null, AnalyticsFilters.DEFAULT_LIMIT));
    }

    @Transactional(readOnly = true)
    public PlayerAnalyticsResponse currentPlayerAnalytics(AnalyticsFilters requestedFilters) {
        AuthenticatedActor actor = requirePlayer();
        UUID profileId = actor.requireProfileId();
        if (requestedFilters.profileId() != null && !requestedFilters.profileId().equals(profileId)) {
            throw new AccessDeniedException("Players can only view their own private analytics.");
        }
        AnalyticsFilters filters = new AnalyticsFilters(
                requestedFilters.tournamentId(),
                requestedFilters.teamId(),
                profileId,
                requestedFilters.heroId(),
                requestedFilters.from(),
                requestedFilters.to(),
                requestedFilters.limit());

        return new PlayerAnalyticsResponse(
                analyticsQueryService.protectedPlayerMetrics(filters),
                analyticsQueryService.protectedHeroMetrics(filters),
                List.of());
    }

    @Transactional(readOnly = true)
    public CurrentTeamAnalyticsResponse currentTeamAnalytics() {
        return currentTeamAnalytics(new AnalyticsFilters(null, null, null, null, AnalyticsFilters.DEFAULT_LIMIT));
    }

    @Transactional(readOnly = true)
    public CurrentTeamAnalyticsResponse currentTeamAnalytics(AnalyticsFilters requestedFilters) {
        AuthenticatedActor actor = requirePlayer();

        return teamRepository.findCurrentTeamForProfile(actor.requireProfileId())
                .map(team -> {
                    ensureRequestedTeamIsCurrent(requestedFilters.teamId(), team);
                    ensureRequestedProfileIsTeamMember(requestedFilters.profileId(), team);
                    AnalyticsFilters filters = new AnalyticsFilters(
                            requestedFilters.tournamentId(),
                            team.id(),
                            requestedFilters.profileId(),
                            requestedFilters.heroId(),
                            requestedFilters.from(),
                            requestedFilters.to(),
                            requestedFilters.limit());
                    return new CurrentTeamAnalyticsResponse(
                            TeamResponse.from(team),
                            analyticsQueryService.protectedTeamMetrics(filters),
                            analyticsQueryService.protectedPlayerMetrics(filters),
                            List.of());
                })
                .orElseGet(() -> {
                    if (requestedFilters.teamId() != null) {
                        throw new AccessDeniedException("Players can only view analytics for their current team.");
                    }
                    return new CurrentTeamAnalyticsResponse(null, List.of(), List.of(), List.of());
                });
    }

    @Transactional(readOnly = true)
    public OrganizerAnalyticsResponse organizerAnalytics() {
        return organizerAnalytics(new AnalyticsFilters(null, null, null, null, AnalyticsFilters.DEFAULT_LIMIT));
    }

    @Transactional(readOnly = true)
    public OrganizerAnalyticsResponse organizerAnalytics(AnalyticsFilters filters) {
        AuthenticatedActor actor = requireOrganizerOrAdmin();
        var counts = roleBasedAnalyticsRepository.findOrganizerCounts(actor.requireProfileId(), actor.isAdmin(), filters);

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
        return organizerTournamentAnalytics(
                tournamentId,
                new AnalyticsFilters(tournamentId, null, null, null, AnalyticsFilters.DEFAULT_LIMIT));
    }

    @Transactional(readOnly = true)
    public OrganizerTournamentAnalyticsResponse organizerTournamentAnalytics(
            UUID tournamentId,
            AnalyticsFilters requestedFilters
    ) {
        AuthenticatedActor actor = requireOrganizerOrAdmin();
        if (requestedFilters.tournamentId() != null && !requestedFilters.tournamentId().equals(tournamentId)) {
            throw new BadRequestException("Tournament filter does not match the route tournament.");
        }
        tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));
        if (!tournamentRepository.canManage(tournamentId, actor.requireProfileId(), actor.isAdmin())) {
            throw new AccessDeniedException("Only tournament organizers can view private tournament analytics.");
        }

        AnalyticsFilters filters = requestedFilters.withTournamentId(tournamentId);
        var operations = roleBasedAnalyticsRepository.findTournamentOperationalMetrics(tournamentId, filters);
        var teamMetrics = analyticsQueryService.protectedTeamMetrics(filters);

        return new OrganizerTournamentAnalyticsResponse(
                tournamentId,
                operations.gamesProcessed(),
                operations.matchesWithoutImport(),
                operations.importCoveragePercent(),
                operations.avgDurationSeconds(),
                analyticsQueryService.protectedTournamentMetrics(filters).orElse(null),
                teamMetrics,
                analyticsQueryService.protectedHeroMetrics(filters),
                teamMetrics,
                roleBasedAnalyticsRepository.findRecentImports(tournamentId, filters.withLimit(RECENT_IMPORTS))
                        .stream()
                        .map(RecentImportResponse::from)
                        .toList());
    }

    private void ensureRequestedTeamIsCurrent(UUID requestedTeamId, Team currentTeam) {
        if (requestedTeamId != null && !requestedTeamId.equals(currentTeam.id())) {
            throw new AccessDeniedException("Players can only view analytics for their current team.");
        }
    }

    private void ensureRequestedProfileIsTeamMember(UUID requestedProfileId, Team currentTeam) {
        if (requestedProfileId == null) {
            return;
        }
        if (requestedProfileId.equals(currentTeam.captainProfileId())
                || teamMemberRepository.existsActive(currentTeam.id(), requestedProfileId)) {
            return;
        }

        throw new AccessDeniedException("Players can only filter team analytics to active members of their team.");
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
