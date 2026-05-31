package si.um.feri.dotaops.backend.team.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedProfile;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.auth.service.SupabasePrincipal;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.ResourceNotFoundException;
import si.um.feri.dotaops.backend.profile.repository.ProfileRepository;
import si.um.feri.dotaops.backend.team.domain.Team;
import si.um.feri.dotaops.backend.team.domain.TeamInvitation;
import si.um.feri.dotaops.backend.team.domain.TeamInvitationStatus;
import si.um.feri.dotaops.backend.team.domain.TeamManualPlayer;
import si.um.feri.dotaops.backend.team.domain.TeamMemberRole;
import si.um.feri.dotaops.backend.team.repository.CreateTeamInvitationCommand;
import si.um.feri.dotaops.backend.team.repository.CreateTeamMemberCommand;
import si.um.feri.dotaops.backend.team.repository.CreateTeamManualPlayerCommand;
import si.um.feri.dotaops.backend.team.repository.TeamInvitationRepository;
import si.um.feri.dotaops.backend.team.repository.TeamManualPlayerRepository;
import si.um.feri.dotaops.backend.team.repository.TeamMemberRepository;
import si.um.feri.dotaops.backend.team.repository.TeamRepository;
import si.um.feri.dotaops.backend.team.repository.TeamRosterLimitRepository;
import si.um.feri.dotaops.backend.team.repository.UpdateTeamManualPlayerCommand;
import si.um.feri.dotaops.backend.team.web.AddTeamMemberRequest;
import si.um.feri.dotaops.backend.team.web.CreateTeamInvitationRequest;
import si.um.feri.dotaops.backend.team.web.CreateTeamManualPlayerRequest;
import si.um.feri.dotaops.backend.team.web.CurrentTeamResponse;
import si.um.feri.dotaops.backend.team.web.TeamInvitationResponse;
import si.um.feri.dotaops.backend.team.web.TeamManualPlayerResponse;
import si.um.feri.dotaops.backend.team.web.TeamMemberResponse;
import si.um.feri.dotaops.backend.team.web.TeamResponse;
import si.um.feri.dotaops.backend.team.web.UpdateTeamMemberRequest;
import si.um.feri.dotaops.backend.team.web.UpdateTeamManualPlayerRequest;
import si.um.feri.dotaops.backend.tournament.domain.TournamentSettings;

@Service
public class TeamRosterService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamManualPlayerRepository teamManualPlayerRepository;
    private final TeamInvitationRepository teamInvitationRepository;
    private final TeamRosterLimitRepository teamRosterLimitRepository;
    private final ProfileRepository profileRepository;
    private final CurrentUserProvider currentUserProvider;

    public TeamRosterService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TeamManualPlayerRepository teamManualPlayerRepository,
            TeamInvitationRepository teamInvitationRepository,
            TeamRosterLimitRepository teamRosterLimitRepository,
            ProfileRepository profileRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamManualPlayerRepository = teamManualPlayerRepository;
        this.teamInvitationRepository = teamInvitationRepository;
        this.teamRosterLimitRepository = teamRosterLimitRepository;
        this.profileRepository = profileRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> listActiveMembers(UUID teamId) {
        ensureTeamExists(teamId);

        return teamMemberRepository.findActiveByTeamId(teamId)
                .stream()
                .map(TeamMemberResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> listActiveMembersByTeamSlug(String slug) {
        String normalizedSlug = normalizeSlug(slug);
        Team team = teamRepository.findBySlug(normalizedSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "slug", normalizedSlug));

        return teamMemberRepository.findActiveByTeamId(team.id())
                .stream()
                .map(TeamMemberResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CurrentTeamResponse getCurrentTeam() {
        AuthenticatedProfile currentProfile = currentUserProvider.requireProfile();

        return teamRepository.findCurrentTeamForProfile(currentProfile.profileId())
                .map(team -> currentTeamResponse(currentProfile, team))
                .orElseGet(CurrentTeamResponse::none);
    }

    @Transactional(readOnly = true)
    public List<TeamManualPlayerResponse> listManualPlayers(UUID teamId) {
        ensureTeamExists(teamId);

        return manualPlayersForTeam(teamId);
    }

    @Transactional(readOnly = true)
    public List<TeamManualPlayerResponse> listManualPlayersByTeamSlug(String slug) {
        String normalizedSlug = normalizeSlug(slug);
        Team team = teamRepository.findBySlug(normalizedSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "slug", normalizedSlug));

        return manualPlayersForTeam(team.id());
    }

    @Transactional
    public TeamMemberResponse addMember(UUID teamId, AddTeamMemberRequest request) {
        Team team = ensureTeamExists(teamId);
        AuthenticatedProfile currentProfile = currentUserProvider.requireProfile();
        ensureCanManageTeam(currentProfile, team);

        UUID targetProfileId = request.profileId();
        profileRepository.findById(targetProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", "id", targetProfileId));

        if (teamMemberRepository.existsActive(teamId, targetProfileId)) {
            throw new BadRequestException("Profile is already an active team member.");
        }
        ensureRosterCapacity(teamId, 1);

        try {
            return TeamMemberResponse.from(teamMemberRepository.create(new CreateTeamMemberCommand(
                    teamId,
                    targetProfileId,
                    resolveMemberRole(request.role()))));
        } catch (DataIntegrityViolationException exception) {
            throw membershipConstraintException(exception);
        }
    }

    @Transactional
    public TeamMemberResponse updateMemberRole(UUID teamId, UUID memberId, UpdateTeamMemberRequest request) {
        Team team = ensureTeamExists(teamId);
        AuthenticatedProfile currentProfile = currentUserProvider.requireProfile();
        ensureCanManageTeam(currentProfile, team);

        return teamMemberRepository.updateRole(teamId, memberId, request.role())
                .map(TeamMemberResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Active team member", "id", memberId));
    }

    @Transactional
    public TeamMemberResponse deactivateMember(UUID teamId, UUID memberId) {
        Team team = ensureTeamExists(teamId);
        AuthenticatedProfile currentProfile = currentUserProvider.requireProfile();
        ensureCanManageTeam(currentProfile, team);

        return teamMemberRepository.deactivate(teamId, memberId)
                .map(TeamMemberResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Active team member", "id", memberId));
    }

    @Transactional
    public TeamManualPlayerResponse createManualPlayer(UUID teamId, CreateTeamManualPlayerRequest request) {
        Team team = ensureTeamExists(teamId);
        AuthenticatedProfile currentProfile = currentUserProvider.requireProfile();
        ensureCanManageTeam(currentProfile, team);
        ensureRosterCapacity(teamId, 1);

        try {
            return TeamManualPlayerResponse.from(teamManualPlayerRepository.create(new CreateTeamManualPlayerCommand(
                    teamId,
                    normalizeRequired(request.displayName(), "Manual player display name is required."),
                    normalizeOptional(request.nickname()),
                    normalizeOptional(request.note()))));
        } catch (DataIntegrityViolationException exception) {
            throw manualPlayerConstraintException(exception);
        }
    }

    @Transactional
    public TeamManualPlayerResponse updateManualPlayer(
            UUID teamId,
            UUID manualPlayerId,
            UpdateTeamManualPlayerRequest request
    ) {
        if (!request.hasChanges()) {
            throw new BadRequestException("At least one manual player field must be provided.");
        }

        Team team = ensureTeamExists(teamId);
        AuthenticatedProfile currentProfile = currentUserProvider.requireProfile();
        ensureCanManageTeam(currentProfile, team);

        try {
            return teamManualPlayerRepository.update(
                            teamId,
                            manualPlayerId,
                            new UpdateTeamManualPlayerCommand(
                                    request.hasDisplayName(),
                                    request.hasDisplayName()
                                            ? normalizeRequired(
                                                    request.displayName(),
                                                    "Manual player display name is required.")
                                            : null,
                                    request.hasNickname(),
                                    request.hasNickname() ? normalizeOptional(request.nickname()) : null,
                                    request.hasNote(),
                                    request.hasNote() ? normalizeOptional(request.note()) : null))
                    .map(TeamManualPlayerResponse::from)
                    .orElseThrow(() -> new ResourceNotFoundException("Manual player", "id", manualPlayerId));
        } catch (DataIntegrityViolationException exception) {
            throw manualPlayerConstraintException(exception);
        }
    }

    @Transactional
    public TeamManualPlayerResponse deleteManualPlayer(UUID teamId, UUID manualPlayerId) {
        Team team = ensureTeamExists(teamId);
        AuthenticatedProfile currentProfile = currentUserProvider.requireProfile();
        ensureCanManageTeam(currentProfile, team);

        return teamManualPlayerRepository.delete(teamId, manualPlayerId)
                .map(TeamManualPlayerResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Manual player", "id", manualPlayerId));
    }

    @Transactional(readOnly = true)
    public List<TeamInvitationResponse> listTeamInvitations(UUID teamId, String requestedStatus) {
        Team team = ensureTeamExists(teamId);
        AuthenticatedProfile currentProfile = currentUserProvider.requireProfile();
        ensureCanManageTeam(currentProfile, team);

        return teamInvitationRepository.findByTeamId(teamId, parseOptionalStatus(requestedStatus))
                .stream()
                .map(TeamInvitationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TeamInvitationResponse> listCurrentUserInvitations(String requestedStatus) {
        AuthenticatedProfile currentProfile = currentUserProvider.requireProfile();
        String email = normalizedCurrentUserEmail();

        return teamInvitationRepository.findForInvitee(
                        currentProfile.profileId(),
                        email,
                        parseOptionalStatus(requestedStatus))
                .stream()
                .map(TeamInvitationResponse::from)
                .toList();
    }

    @Transactional
    public TeamInvitationResponse createInvitation(UUID teamId, CreateTeamInvitationRequest request) {
        Team team = ensureTeamExists(teamId);
        AuthenticatedProfile currentProfile = currentUserProvider.requireProfile();
        ensureCanManageTeam(currentProfile, team);

        UUID inviteeProfileId = request.inviteeProfileId();
        String inviteeEmail = normalizeEmail(request.inviteeEmail());
        if (inviteeProfileId == null && inviteeEmail == null) {
            throw new BadRequestException("Invitee profile id or invitee email is required.");
        }

        if (inviteeProfileId != null) {
            profileRepository.findById(inviteeProfileId)
                    .orElseThrow(() -> new ResourceNotFoundException("Profile", "id", inviteeProfileId));

            if (inviteeEmail != null && !profileRepository.emailMatchesProfileAuthUser(inviteeProfileId, inviteeEmail)) {
                throw new BadRequestException("Invitee profile id and invitee email must reference the same user.");
            }

            if (teamMemberRepository.existsActive(teamId, inviteeProfileId)) {
                throw new BadRequestException("Profile is already an active team member.");
            }

            if (teamInvitationRepository.findPendingByTeamAndInviteeProfile(teamId, inviteeProfileId).isPresent()) {
                throw new BadRequestException("Pending invitation already exists for this team and profile.");
            }
        }

        if (inviteeEmail != null
                && teamInvitationRepository.findPendingByTeamAndInviteeEmail(teamId, inviteeEmail).isPresent()) {
            throw new BadRequestException("Pending invitation already exists for this team and email.");
        }

        try {
            return TeamInvitationResponse.from(teamInvitationRepository.create(new CreateTeamInvitationCommand(
                    teamId,
                    currentProfile.profileId(),
                    inviteeProfileId,
                    inviteeEmail,
                    resolveMemberRole(request.proposedRole()),
                    request.expiresAt())));
        } catch (DataIntegrityViolationException exception) {
            throw invitationConstraintException(exception);
        }
    }

    @Transactional
    public TeamInvitationResponse acceptInvitation(UUID invitationId) {
        TeamInvitation invitation = findInvitation(invitationId);
        AuthenticatedProfile currentProfile = currentUserProvider.requireProfile();
        ensureCanRespondToInvitation(currentProfile, invitation);
        ensurePendingAndNotExpired(invitation);

        try {
            TeamInvitation accepted = teamInvitationRepository.accept(invitationId, currentProfile.profileId())
                    .orElseThrow(() -> new BadRequestException("Only pending invitations can be accepted."));

            teamMemberRepository.create(new CreateTeamMemberCommand(
                    invitation.teamId(),
                    currentProfile.profileId(),
                    accepted.proposedRole()));

            return TeamInvitationResponse.from(accepted);
        } catch (DataIntegrityViolationException exception) {
            throw membershipConstraintException(exception);
        }
    }

    @Transactional
    public TeamInvitationResponse declineInvitation(UUID invitationId) {
        TeamInvitation invitation = findInvitation(invitationId);
        AuthenticatedProfile currentProfile = currentUserProvider.requireProfile();
        ensureCanRespondToInvitation(currentProfile, invitation);
        ensurePendingAndNotExpired(invitation);

        return teamInvitationRepository.decline(invitationId)
                .map(TeamInvitationResponse::from)
                .orElseThrow(() -> new BadRequestException("Only pending invitations can be declined."));
    }

    @Transactional
    public TeamInvitationResponse cancelInvitation(UUID invitationId) {
        TeamInvitation invitation = findInvitation(invitationId);
        Team team = ensureTeamExists(invitation.teamId());
        AuthenticatedProfile currentProfile = currentUserProvider.requireProfile();
        ensureCanManageTeam(currentProfile, team);
        ensurePendingAndNotExpired(invitation);

        return teamInvitationRepository.cancel(invitationId)
                .map(TeamInvitationResponse::from)
                .orElseThrow(() -> new BadRequestException("Only pending invitations can be cancelled."));
    }

    private Team ensureTeamExists(UUID teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", teamId));
    }

    private TeamInvitation findInvitation(UUID invitationId) {
        return teamInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Team invitation", "id", invitationId));
    }

    private void ensureCanManageTeam(AuthenticatedProfile profile, Team team) {
        if (canManageTeam(profile, team)) {
            return;
        }

        throw new AccessDeniedException("Only the team captain or an organizer can manage this team.");
    }

    private CurrentTeamResponse currentTeamResponse(AuthenticatedProfile profile, Team team) {
        List<TeamMemberResponse> members = teamMemberRepository.findActiveByTeamId(team.id())
                .stream()
                .map(TeamMemberResponse::from)
                .toList();
        List<TeamManualPlayerResponse> manualPlayers = manualPlayersForTeam(team.id());
        boolean captain = profile.profileId().equals(team.captainProfileId());

        return new CurrentTeamResponse(
                TeamResponse.from(team, manualPlayers),
                members,
                manualPlayers,
                captain,
                canManageTeam(profile, team),
                captain ? "Resolved from current captain ownership." : "Resolved from active team membership.");
    }

    private boolean canManageTeam(AuthenticatedProfile profile, Team team) {
        return profile.role() == ProfileRole.ORGANIZER
                || profile.role() == ProfileRole.ADMIN
                || profile.profileId().equals(team.captainProfileId());
    }

    private void ensureCanRespondToInvitation(AuthenticatedProfile profile, TeamInvitation invitation) {
        boolean profileMatches = profile.profileId().equals(invitation.inviteeProfileId());
        String currentEmail = normalizedCurrentUserEmail();
        boolean emailMatches = currentEmail != null
                && invitation.inviteeEmail() != null
                && currentEmail.equals(invitation.inviteeEmail().trim().toLowerCase(Locale.ROOT));

        if (invitation.inviteeProfileId() != null && invitation.inviteeEmail() != null) {
            if (!profileMatches || !emailMatches) {
                throw new AccessDeniedException("Only the invited user can respond to this invitation.");
            }

            return;
        }

        if (!profileMatches && !emailMatches) {
            throw new AccessDeniedException("Only the invited user can respond to this invitation.");
        }
    }

    private void ensurePendingAndNotExpired(TeamInvitation invitation) {
        if (invitation.status() != TeamInvitationStatus.PENDING) {
            throw new BadRequestException("Only pending invitations can be changed.");
        }

        OffsetDateTime expiresAt = invitation.expiresAt();
        if (expiresAt != null && expiresAt.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            teamInvitationRepository.expire(invitation.id());
            throw new BadRequestException("Team invitation has expired.");
        }
    }

    private TeamInvitationStatus parseOptionalStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return TeamInvitationStatus.fromDatabaseValue(value);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Unsupported team invitation status.");
        }
    }

    private TeamMemberRole resolveMemberRole(TeamMemberRole role) {
        return role == null ? TeamMemberRole.SUPPORT : role;
    }

    private List<TeamManualPlayerResponse> manualPlayersForTeam(UUID teamId) {
        List<TeamManualPlayer> manualPlayers = teamManualPlayerRepository.findByTeamId(teamId);
        return (manualPlayers == null ? Collections.<TeamManualPlayer>emptyList() : manualPlayers)
                .stream()
                .map(TeamManualPlayerResponse::from)
                .toList();
    }

    private void ensureRosterCapacity(UUID teamId, int additionalPlayers) {
        int rosterLimit = teamRosterLimitRepository.resolveRosterLimit(teamId);
        if (rosterLimit <= 0) {
            rosterLimit = TournamentSettings.DEFAULT_TEAM_SIZE;
        }
        int currentRosterSize = teamMemberRepository.countActiveByTeamId(teamId)
                + teamManualPlayerRepository.countByTeamId(teamId);
        if (currentRosterSize + additionalPlayers > rosterLimit) {
            throw new BadRequestException("Team roster cannot exceed %d players.".formatted(rosterLimit));
        }
    }

    private String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }

        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String normalizeSlug(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Team slug is required.");
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizedCurrentUserEmail() {
        return currentUserProvider.currentUser()
                .map(SupabasePrincipal::email)
                .map(this::normalizeEmail)
                .orElse(null);
    }

    private BadRequestException membershipConstraintException(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        if (message != null && message.contains("team_members_one_active_profile_per_team_idx")) {
            return new BadRequestException("Profile is already an active team member.");
        }

        return new BadRequestException("Team member data violates a database constraint.");
    }

    private BadRequestException invitationConstraintException(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        if (message != null && message.contains("team_invitations_pending_profile_idx")) {
            return new BadRequestException("Pending invitation already exists for this team and profile.");
        }

        if (message != null && message.contains("team_invitations_pending_email_idx")) {
            return new BadRequestException("Pending invitation already exists for this team and email.");
        }

        return new BadRequestException("Team invitation data violates a database constraint.");
    }

    private BadRequestException manualPlayerConstraintException(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        if (message != null && message.contains("team_manual_players_display_name_length")) {
            return new BadRequestException("Manual player display name must be between 1 and 80 characters.");
        }

        return new BadRequestException("Manual player data violates a database constraint.");
    }
}
