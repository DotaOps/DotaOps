package si.um.feri.dotaops.backend.analytics.service;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.um.feri.dotaops.backend.analytics.repository.AnalyticsLookupRepository;
import si.um.feri.dotaops.backend.analytics.web.AnalyticsHeroLookupResponse;
import si.um.feri.dotaops.backend.analytics.web.AnalyticsPlayerLookupResponse;
import si.um.feri.dotaops.backend.analytics.web.AnalyticsTeamLookupResponse;
import si.um.feri.dotaops.backend.analytics.web.AnalyticsTournamentLookupResponse;
import si.um.feri.dotaops.backend.auth.domain.AuthenticatedActor;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;

@Service
public class AnalyticsLookupService {

    private final AnalyticsLookupRepository lookupRepository;
    private final CurrentUserProvider currentUserProvider;

    public AnalyticsLookupService(
            AnalyticsLookupRepository lookupRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.lookupRepository = lookupRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public List<AnalyticsTournamentLookupResponse> organizerTournaments(int limit) {
        AuthenticatedActor actor = requireOrganizerOrAdmin();

        return lookupRepository.findManageableTournaments(actor.requireProfileId(), actor.isAdmin(), safeLimit(limit))
                .stream()
                .map(AnalyticsTournamentLookupResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AnalyticsTeamLookupResponse> currentPlayerTeams(int limit) {
        AuthenticatedActor actor = requirePlayer();

        return lookupRepository.findCurrentTeams(actor.requireProfileId(), safeLimit(limit))
                .stream()
                .map(AnalyticsTeamLookupResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AnalyticsPlayerLookupResponse> teamPlayers(UUID teamId, int limit) {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        UUID profileId = actor.requireProfileId();

        boolean allowed = switch (actor.role()) {
            case ADMIN -> true;
            case PLAYER -> lookupRepository.isActiveTeamMember(teamId, profileId);
            case ORGANIZER -> lookupRepository.teamAppearsInManageableTournament(teamId, profileId, false);
            case VISITOR -> false;
        };
        if (!allowed) {
            throw new AccessDeniedException("Only team members, relevant organizers or admins can list team players.");
        }

        return lookupRepository.findActiveTeamPlayers(teamId, safeLimit(limit))
                .stream()
                .map(AnalyticsPlayerLookupResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AnalyticsHeroLookupResponse> heroes(int limit) {
        return lookupRepository.findHeroes(safeLimit(limit))
                .stream()
                .map(AnalyticsHeroLookupResponse::from)
                .toList();
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

    private int safeLimit(int limit) {
        return Math.min(Math.max(limit <= 0 ? 10 : limit, 1), 100);
    }
}
