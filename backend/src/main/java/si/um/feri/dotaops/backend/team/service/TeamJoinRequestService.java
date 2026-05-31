package si.um.feri.dotaops.backend.team.service;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedProfile;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.ConflictException;
import si.um.feri.dotaops.backend.common.error.ResourceNotFoundException;
import si.um.feri.dotaops.backend.team.domain.Team;
import si.um.feri.dotaops.backend.team.domain.TeamJoinRequest;
import si.um.feri.dotaops.backend.team.domain.TeamJoinRequestStatus;
import si.um.feri.dotaops.backend.team.domain.TeamMemberRole;
import si.um.feri.dotaops.backend.team.repository.CreateTeamJoinRequestCommand;
import si.um.feri.dotaops.backend.team.repository.CreateTeamMemberCommand;
import si.um.feri.dotaops.backend.team.repository.TeamInvitationRepository;
import si.um.feri.dotaops.backend.team.repository.TeamJoinRequestRepository;
import si.um.feri.dotaops.backend.team.repository.TeamManualPlayerRepository;
import si.um.feri.dotaops.backend.team.repository.TeamMemberRepository;
import si.um.feri.dotaops.backend.team.repository.TeamRepository;
import si.um.feri.dotaops.backend.team.repository.TeamRosterLimitRepository;
import si.um.feri.dotaops.backend.team.web.CreateTeamJoinRequestRequest;
import si.um.feri.dotaops.backend.team.web.TeamJoinRequestResponse;
import si.um.feri.dotaops.backend.tournament.domain.TournamentSettings;

@Service
public class TeamJoinRequestService {

    private final TeamJoinRequestRepository joinRequestRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamManualPlayerRepository teamManualPlayerRepository;
    private final TeamInvitationRepository teamInvitationRepository;
    private final TeamRosterLimitRepository teamRosterLimitRepository;
    private final CurrentUserProvider currentUserProvider;

    public TeamJoinRequestService(
            TeamJoinRequestRepository joinRequestRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TeamManualPlayerRepository teamManualPlayerRepository,
            TeamInvitationRepository teamInvitationRepository,
            TeamRosterLimitRepository teamRosterLimitRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.joinRequestRepository = joinRequestRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamManualPlayerRepository = teamManualPlayerRepository;
        this.teamInvitationRepository = teamInvitationRepository;
        this.teamRosterLimitRepository = teamRosterLimitRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public TeamJoinRequestResponse createJoinRequest(UUID teamId, CreateTeamJoinRequestRequest request) {
        Team team = ensureTeamExists(teamId);
        AuthenticatedProfile currentProfile = currentUserProvider.requireProfile();
        UUID requesterProfileId = currentProfile.profileId();

        ensureRequesterIsNotMember(team, requesterProfileId);
        if (joinRequestRepository.findPendingByTeamAndRequester(teamId, requesterProfileId).isPresent()) {
            throw new BadRequestException("Pending join request already exists for this team.");
        }

        if (teamInvitationRepository.findPendingByTeamAndInviteeProfile(teamId, requesterProfileId).isPresent()) {
            throw new BadRequestException("Pending invitation already exists for this team and profile.");
        }

        ensureTeamHasOpenRosterSlot(teamId);

        try {
            return TeamJoinRequestResponse.from(joinRequestRepository.create(new CreateTeamJoinRequestCommand(
                    teamId,
                    requesterProfileId,
                    normalizeOptional(request.message()))));
        } catch (DataIntegrityViolationException exception) {
            throw joinRequestConstraintException(exception);
        }
    }

    @Transactional(readOnly = true)
    public List<TeamJoinRequestResponse> listCurrentUserJoinRequests(String requestedStatus) {
        AuthenticatedProfile currentProfile = currentUserProvider.requireProfile();

        return joinRequestRepository.findByRequesterProfileId(
                        currentProfile.profileId(),
                        parseOptionalStatus(requestedStatus))
                .stream()
                .map(TeamJoinRequestResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TeamJoinRequestResponse> listTeamJoinRequests(UUID teamId, String requestedStatus) {
        Team team = ensureTeamExists(teamId);
        AuthenticatedProfile currentProfile = currentUserProvider.requireProfile();
        ensureCanViewTeamJoinRequests(currentProfile, team);

        return joinRequestRepository.findByTeamId(teamId, parseOptionalStatus(requestedStatus))
                .stream()
                .map(TeamJoinRequestResponse::from)
                .toList();
    }

    @Transactional
    public TeamJoinRequestResponse acceptJoinRequest(UUID requestId) {
        TeamJoinRequest joinRequest = findJoinRequest(requestId);
        Team team = ensureTeamExists(joinRequest.teamId());
        AuthenticatedProfile currentProfile = currentUserProvider.requireProfile();
        ensureCaptain(currentProfile, team);
        ensurePending(joinRequest);
        ensureRequesterIsNotMember(team, joinRequest.requesterProfileId());
        ensureTeamHasOpenRosterSlot(team.id());

        try {
            teamMemberRepository.create(new CreateTeamMemberCommand(
                    team.id(),
                    joinRequest.requesterProfileId(),
                    TeamMemberRole.SUPPORT));

            return joinRequestRepository.accept(requestId, currentProfile.profileId())
                    .map(TeamJoinRequestResponse::from)
                    .orElseThrow(() -> new BadRequestException("Only pending join requests can be accepted."));
        } catch (DataIntegrityViolationException exception) {
            throw membershipConstraintException(exception);
        }
    }

    @Transactional
    public TeamJoinRequestResponse declineJoinRequest(UUID requestId) {
        TeamJoinRequest joinRequest = findJoinRequest(requestId);
        Team team = ensureTeamExists(joinRequest.teamId());
        AuthenticatedProfile currentProfile = currentUserProvider.requireProfile();
        ensureCaptain(currentProfile, team);
        ensurePending(joinRequest);

        return joinRequestRepository.decline(requestId, currentProfile.profileId())
                .map(TeamJoinRequestResponse::from)
                .orElseThrow(() -> new BadRequestException("Only pending join requests can be declined."));
    }

    @Transactional
    public TeamJoinRequestResponse cancelJoinRequest(UUID requestId) {
        TeamJoinRequest joinRequest = findJoinRequest(requestId);
        AuthenticatedProfile currentProfile = currentUserProvider.requireProfile();
        if (!currentProfile.profileId().equals(joinRequest.requesterProfileId())) {
            throw new AccessDeniedException("Only the requester can cancel this join request.");
        }
        ensurePending(joinRequest);

        return joinRequestRepository.cancel(requestId, currentProfile.profileId())
                .map(TeamJoinRequestResponse::from)
                .orElseThrow(() -> new BadRequestException("Only pending join requests can be cancelled."));
    }

    private Team ensureTeamExists(UUID teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", teamId));
    }

    private TeamJoinRequest findJoinRequest(UUID requestId) {
        return joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Team join request", "id", requestId));
    }

    private void ensureRequesterIsNotMember(Team team, UUID requesterProfileId) {
        if (requesterProfileId.equals(team.captainProfileId())
                || teamMemberRepository.existsActive(team.id(), requesterProfileId)) {
            throw new BadRequestException("Profile is already an active team member.");
        }
    }

    private void ensureCanViewTeamJoinRequests(AuthenticatedProfile profile, Team team) {
        if (profile.role() == ProfileRole.ORGANIZER
                || profile.role() == ProfileRole.ADMIN
                || profile.profileId().equals(team.captainProfileId())) {
            return;
        }

        throw new AccessDeniedException("Only the team captain or an organizer can view join requests.");
    }

    private void ensureCaptain(AuthenticatedProfile profile, Team team) {
        if (profile.profileId().equals(team.captainProfileId())) {
            return;
        }

        throw new AccessDeniedException("Only the team captain can resolve join requests.");
    }

    private void ensurePending(TeamJoinRequest joinRequest) {
        if (joinRequest.status() != TeamJoinRequestStatus.PENDING) {
            throw new BadRequestException("Only pending join requests can be changed.");
        }
    }

    private void ensureTeamHasOpenRosterSlot(UUID teamId) {
        int rosterLimit = teamRosterLimitRepository.resolveRosterLimit(teamId);
        if (rosterLimit <= 0) {
            rosterLimit = TournamentSettings.DEFAULT_TEAM_SIZE;
        }
        int currentRosterSize = teamMemberRepository.countActiveByTeamId(teamId)
                + teamManualPlayerRepository.countByTeamId(teamId);
        if (currentRosterSize >= rosterLimit) {
            throw new ConflictException("Team roster is full.");
        }
    }

    private TeamJoinRequestStatus parseOptionalStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return TeamJoinRequestStatus.fromDatabaseValue(value);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Unsupported team join request status.");
        }
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private BadRequestException joinRequestConstraintException(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        if (message != null && message.contains("team_join_requests_pending_requester_idx")) {
            return new BadRequestException("Pending join request already exists for this team.");
        }

        return new BadRequestException("Team join request data violates a database constraint.");
    }

    private BadRequestException membershipConstraintException(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        if (message != null && message.contains("team_members_one_active_profile_per_team_idx")) {
            return new BadRequestException("Profile is already an active team member.");
        }

        return new BadRequestException("Team member data violates a database constraint.");
    }
}
