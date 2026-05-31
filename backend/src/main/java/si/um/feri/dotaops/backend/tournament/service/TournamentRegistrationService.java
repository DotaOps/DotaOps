package si.um.feri.dotaops.backend.tournament.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedActor;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.ApiException;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.ConflictException;
import si.um.feri.dotaops.backend.common.error.ResourceNotFoundException;
import si.um.feri.dotaops.backend.common.security.DatabaseActorContext;
import si.um.feri.dotaops.backend.notification.service.NotificationOutboxService;
import si.um.feri.dotaops.backend.team.domain.Team;
import si.um.feri.dotaops.backend.team.repository.TeamMemberRepository;
import si.um.feri.dotaops.backend.team.repository.TeamRepository;
import si.um.feri.dotaops.backend.tournament.domain.Tournament;
import si.um.feri.dotaops.backend.tournament.domain.TournamentRegistration;
import si.um.feri.dotaops.backend.tournament.domain.TournamentRegistrationStatus;
import si.um.feri.dotaops.backend.tournament.domain.TournamentStatus;
import si.um.feri.dotaops.backend.tournament.dto.CreateTournamentRegistrationRequest;
import si.um.feri.dotaops.backend.tournament.dto.ReviewTournamentRegistrationRequest;
import si.um.feri.dotaops.backend.tournament.dto.TournamentRegistrationResponse;
import si.um.feri.dotaops.backend.tournament.repository.CreateTournamentRegistrationCommand;
import si.um.feri.dotaops.backend.tournament.repository.TournamentRegistrationRepository;
import si.um.feri.dotaops.backend.tournament.repository.TournamentRepository;

@Service
public class TournamentRegistrationService {

    private final TournamentRegistrationRepository registrationRepository;
    private final TournamentRepository tournamentRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final CurrentUserProvider currentUserProvider;
    private final DatabaseActorContext databaseActorContext;
    private final NotificationOutboxService notificationOutboxService;

    public TournamentRegistrationService(
            TournamentRegistrationRepository registrationRepository,
            TournamentRepository tournamentRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            CurrentUserProvider currentUserProvider,
            DatabaseActorContext databaseActorContext,
            NotificationOutboxService notificationOutboxService
    ) {
        this.registrationRepository = registrationRepository;
        this.tournamentRepository = tournamentRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.currentUserProvider = currentUserProvider;
        this.databaseActorContext = databaseActorContext;
        this.notificationOutboxService = notificationOutboxService;
    }

    @Transactional(readOnly = true)
    public List<TournamentRegistrationResponse> listOrganizerRegistrations(UUID tournamentId, String requestedStatus) {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        ensureTournamentExists(tournamentId);
        ensureCanManage(actor, tournamentId);

        return registrationRepository.findByTournamentId(tournamentId, parseOptionalStatus(requestedStatus))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TournamentRegistrationResponse> listTeamRegistrations(UUID teamId) {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", teamId));
        ensureCanViewTeamRegistrations(actor, team);

        return registrationRepository.findByTeamId(teamId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TournamentRegistrationResponse registerTeam(UUID tournamentId, CreateTournamentRegistrationRequest request) {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        UUID profileId = actor.requireProfileId();
        Tournament tournament = ensureTournamentExists(tournamentId);
        Team team = teamRepository.findById(request.teamId())
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", request.teamId()));

        ensureCanRegisterTeam(actor, team);
        ensureRegistrationWindowOpen(tournament);
        int rosterSize = tournament.settings().teamSize();
        ensureTeamNotAlreadyRegistered(tournament.id(), team.id());
        ensureRosterReady(team.id(), rosterSize);
        databaseActorContext.apply(actor);

        try {
            TournamentRegistration registration = registrationRepository.create(
                    new CreateTournamentRegistrationCommand(
                            tournament.id(),
                            team.id(),
                            profileId,
                            normalizeOptional(request.message()),
                            normalizeEmail(request.contactEmail())),
                    rosterSize);

            notificationOutboxService.createTeamApplicationSubmittedNotification(tournament, registration);
            return toResponse(registration);
        } catch (DataIntegrityViolationException exception) {
            throw registrationConstraintException(exception);
        }
    }

    @Transactional
    public TournamentRegistrationResponse approveRegistration(
            UUID tournamentId,
            UUID registrationId,
            ReviewTournamentRegistrationRequest request
    ) {
        return reviewRegistration(
                tournamentId,
                registrationId,
                TournamentRegistrationStatus.APPROVED,
                request == null ? null : request.seedNumber());
    }

    @Transactional
    public TournamentRegistrationResponse rejectRegistration(UUID tournamentId, UUID registrationId) {
        return reviewRegistration(tournamentId, registrationId, TournamentRegistrationStatus.REJECTED, null);
    }

    @Transactional
    public TournamentRegistrationResponse waitlistRegistration(UUID tournamentId, UUID registrationId) {
        return reviewRegistration(tournamentId, registrationId, TournamentRegistrationStatus.WAITLISTED, null);
    }

    @Transactional
    public TournamentRegistrationResponse checkInRegistration(UUID tournamentId, UUID registrationId) {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        Tournament tournament = ensureTournamentExists(tournamentId);
        TournamentRegistration registration = findRegistration(tournamentId, registrationId);
        ensureCanCheckIn(actor, registration, tournamentId);
        ensureCheckInAllowed(tournament, registration);
        databaseActorContext.apply(actor);

        return registrationRepository.checkIn(registrationId, tournamentId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament registration", "id", registrationId));
    }

    private TournamentRegistrationResponse reviewRegistration(
            UUID tournamentId,
            UUID registrationId,
            TournamentRegistrationStatus status,
            Integer seedNumber
    ) {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        Tournament tournament = ensureTournamentExists(tournamentId);
        TournamentRegistration existing = findRegistration(tournamentId, registrationId);
        ensureCanManage(actor, tournamentId);
        validateReview(status, seedNumber, tournament, existing);
        databaseActorContext.apply(actor);

        try {
            TournamentRegistration updated = registrationRepository.updateStatus(
                            registrationId,
                            tournamentId,
                            status,
                            actor.requireProfileId(),
                            seedNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("Tournament registration", "id", registrationId));

            notifyReviewResult(status, updated);
            return toResponse(updated);
        } catch (DataIntegrityViolationException exception) {
            throw registrationConstraintException(exception);
        }
    }

    private void notifyReviewResult(TournamentRegistrationStatus status, TournamentRegistration registration) {
        if (status == TournamentRegistrationStatus.APPROVED) {
            notificationOutboxService.createTeamApplicationApprovedNotification(registration);
        } else if (status == TournamentRegistrationStatus.REJECTED) {
            notificationOutboxService.createTeamApplicationRejectedNotification(registration);
        }
    }

    private Tournament ensureTournamentExists(UUID tournamentId) {
        return tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));
    }

    private TournamentRegistration findRegistration(UUID tournamentId, UUID registrationId) {
        return registrationRepository.findByIdAndTournamentId(registrationId, tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament registration", "id", registrationId));
    }

    private void ensureCanRegisterTeam(AuthenticatedActor actor, Team team) {
        if (actor.requireProfileId().equals(team.captainProfileId())) {
            return;
        }

        throw new AccessDeniedException("Only the team captain can register this team for a tournament.");
    }

    private void ensureCanViewTeamRegistrations(AuthenticatedActor actor, Team team) {
        UUID profileId = actor.requireProfileId();
        if (actor.isAdmin()
                || profileId.equals(team.captainProfileId())
                || teamMemberRepository.existsActive(team.id(), profileId)) {
            return;
        }

        throw new AccessDeniedException("Only active team members can view this team's tournament registrations.");
    }

    private void ensureTeamNotAlreadyRegistered(UUID tournamentId, UUID teamId) {
        if (registrationRepository.existsByTournamentIdAndTeamId(tournamentId, teamId)) {
            throw new ConflictException("Team is already registered for this tournament.");
        }
    }

    private void ensureCanManage(AuthenticatedActor actor, UUID tournamentId) {
        UUID profileId = actor.requireProfileId();
        if (tournamentRepository.canManage(tournamentId, profileId, actor.isAdmin())) {
            return;
        }

        throw new AccessDeniedException("Only the tournament owner, tournament organizers, or admins can manage registrations.");
    }

    private void ensureCanCheckIn(AuthenticatedActor actor, TournamentRegistration registration, UUID tournamentId) {
        UUID profileId = actor.requireProfileId();
        if (actor.isAdmin()
                || profileId.equals(registration.captainProfileId())
                || tournamentRepository.canManage(tournamentId, profileId, actor.isAdmin())) {
            return;
        }

        throw new AccessDeniedException("Only the registered captain or tournament organizer can check in this team.");
    }

    private void ensureRegistrationWindowOpen(Tournament tournament) {
        if (tournament.status() == TournamentStatus.DRAFT
                || tournament.status() == TournamentStatus.LIVE
                || tournament.status() == TournamentStatus.ARCHIVED) {
            throw new ConflictException("Tournament is not open for registrations.");
        }

        if (tournament.status() == TournamentStatus.FINISHED) {
            throw new ConflictException("Finished tournaments do not accept registrations.");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (tournament.registrationOpensAt() != null && now.isBefore(tournament.registrationOpensAt())) {
            throw new BadRequestException("Tournament registration is not open yet.");
        }

        if (tournament.registrationClosesAt() != null && now.isAfter(tournament.registrationClosesAt())) {
            throw new BadRequestException("Tournament registration is closed.");
        }
    }

    private void ensureRosterReady(UUID teamId, int requiredRosterSize) {
        int activeRosterMembers = registrationRepository.countActiveRosterMembers(teamId);
        if (activeRosterMembers < requiredRosterSize) {
            throw new BadRequestException("Team must have at least %d active roster participants before registration."
                    .formatted(requiredRosterSize));
        }

        if (activeRosterMembers > requiredRosterSize) {
            throw new BadRequestException("Team cannot have more than %d active roster participants for this tournament."
                    .formatted(requiredRosterSize));
        }
    }

    private void validateReview(
            TournamentRegistrationStatus status,
            Integer seedNumber,
            Tournament tournament,
            TournamentRegistration registration
    ) {
        if (registration.checkedInAt() != null) {
            throw new ConflictException("Checked-in registrations can no longer be reviewed.");
        }

        if (status == TournamentRegistrationStatus.APPROVED) {
            long approvedCount = registrationRepository.countApprovedRegistrations(tournament.id(), registration.id());
            if (approvedCount >= tournament.maxTeams()) {
                throw new ConflictException("Tournament already has the maximum number of approved teams.");
            }

            if (seedNumber != null && seedNumber < 1) {
                throw new BadRequestException("Registration seed number must be positive.");
            }
            return;
        }

        if (seedNumber != null) {
            throw new BadRequestException("Seed number is only allowed when approving a registration.");
        }
    }

    private void ensureCheckInAllowed(Tournament tournament, TournamentRegistration registration) {
        if (registration.status() != TournamentRegistrationStatus.APPROVED) {
            throw new ConflictException("Only approved registrations can check in.");
        }

        if (!tournament.settings().checkInEnabled()) {
            throw new ConflictException("Check-in is not enabled for this tournament.");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (tournament.checkInOpensAt() != null && now.isBefore(tournament.checkInOpensAt())) {
            throw new BadRequestException("Tournament check-in is not open yet.");
        }

        if (tournament.checkInClosesAt() != null && now.isAfter(tournament.checkInClosesAt())) {
            throw new BadRequestException("Tournament check-in is closed.");
        }
    }

    private TournamentRegistrationResponse toResponse(TournamentRegistration registration) {
        return TournamentRegistrationResponse.from(
                registration,
                registrationRepository.findMembers(registration.id()));
    }

    private TournamentRegistrationStatus parseOptionalStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return TournamentRegistrationStatus.fromDatabaseValue(value);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Unsupported tournament registration status.");
        }
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String normalizeEmail(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private ApiException registrationConstraintException(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();

        if (message != null && message.contains("tournament_registrations_tournament_id_team_id_key")) {
            return new ConflictException("Team is already registered for this tournament.");
        }

        if (message != null && message.contains("tournament_registrations_seed_idx")) {
            return new BadRequestException("Registration seed number is already in use for this tournament.");
        }

        if (message != null && message.contains("tournament_registration_members_validate_starters")) {
            return new BadRequestException("A tournament registration has too many starters for the tournament team size.");
        }

        return new BadRequestException("Tournament registration data violates a database constraint.");
    }
}
