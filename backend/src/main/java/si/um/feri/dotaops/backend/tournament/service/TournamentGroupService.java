package si.um.feri.dotaops.backend.tournament.service;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedActor;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.ApiException;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.ConflictException;
import si.um.feri.dotaops.backend.common.error.ResourceNotFoundException;
import si.um.feri.dotaops.backend.common.security.DatabaseActorContext;
import si.um.feri.dotaops.backend.team.domain.Team;
import si.um.feri.dotaops.backend.team.repository.TeamRepository;
import si.um.feri.dotaops.backend.tournament.domain.Tournament;
import si.um.feri.dotaops.backend.tournament.domain.TournamentGroup;
import si.um.feri.dotaops.backend.tournament.domain.TournamentGroupTeam;
import si.um.feri.dotaops.backend.tournament.dto.AddTeamToGroupRequest;
import si.um.feri.dotaops.backend.tournament.dto.CreateTournamentGroupRequest;
import si.um.feri.dotaops.backend.tournament.dto.GroupStandingResponse;
import si.um.feri.dotaops.backend.tournament.dto.TournamentGroupResponse;
import si.um.feri.dotaops.backend.tournament.dto.TournamentGroupTeamResponse;
import si.um.feri.dotaops.backend.tournament.repository.AddTournamentGroupTeamCommand;
import si.um.feri.dotaops.backend.tournament.repository.CreateTournamentGroupCommand;
import si.um.feri.dotaops.backend.tournament.repository.TournamentGroupRepository;
import si.um.feri.dotaops.backend.tournament.repository.TournamentRepository;

@Service
public class TournamentGroupService {

    private final TournamentGroupRepository groupRepository;
    private final TournamentRepository tournamentRepository;
    private final TeamRepository teamRepository;
    private final CurrentUserProvider currentUserProvider;
    private final DatabaseActorContext databaseActorContext;

    public TournamentGroupService(
            TournamentGroupRepository groupRepository,
            TournamentRepository tournamentRepository,
            TeamRepository teamRepository,
            CurrentUserProvider currentUserProvider,
            DatabaseActorContext databaseActorContext
    ) {
        this.groupRepository = groupRepository;
        this.tournamentRepository = tournamentRepository;
        this.teamRepository = teamRepository;
        this.currentUserProvider = currentUserProvider;
        this.databaseActorContext = databaseActorContext;
    }

    @Transactional(readOnly = true)
    public List<TournamentGroupResponse> listPublicGroups(UUID tournamentId) {
        ensurePublicTournamentExists(tournamentId);

        return groupRepository.findPublicByTournamentId(tournamentId)
                .stream()
                .map(TournamentGroupResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TournamentGroupResponse> listOrganizerGroups(UUID tournamentId) {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        ensureTournamentExists(tournamentId);
        ensureCanManage(actor, tournamentId);

        return groupRepository.findByTournamentId(tournamentId)
                .stream()
                .map(TournamentGroupResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GroupStandingResponse> listOrganizerStandings(UUID tournamentId) {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        ensureTournamentExists(tournamentId);
        ensureCanManage(actor, tournamentId);

        return groupRepository.findOrganizerStandingsByTournamentId(tournamentId)
                .stream()
                .map(GroupStandingResponse::from)
                .toList();
    }

    @Transactional
    public TournamentGroupResponse createGroup(UUID tournamentId, CreateTournamentGroupRequest request) {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        ensureTournamentExists(tournamentId);
        ensureCanManage(actor, tournamentId);
        databaseActorContext.apply(actor);

        int sortOrder = request.sortOrder() == null
                ? groupRepository.nextSortOrder(tournamentId)
                : request.sortOrder();

        try {
            TournamentGroup group = groupRepository.create(new CreateTournamentGroupCommand(
                    tournamentId,
                    normalizeGroupName(request.name()),
                    sortOrder));

            return TournamentGroupResponse.from(group);
        } catch (DataIntegrityViolationException exception) {
            throw groupConstraintException(exception);
        }
    }

    @Transactional(readOnly = true)
    public List<TournamentGroupTeamResponse> listPublicGroupTeams(UUID groupId) {
        ensurePublicGroupExists(groupId);

        return groupRepository.findTeamsByGroupId(groupId)
                .stream()
                .map(TournamentGroupTeamResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TournamentGroupTeamResponse> listOrganizerGroupTeams(UUID groupId) {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        TournamentGroup group = findGroup(groupId);
        ensureCanManage(actor, group.tournamentId());

        return groupRepository.findTeamsByGroupId(groupId)
                .stream()
                .map(TournamentGroupTeamResponse::from)
                .toList();
    }

    @Transactional
    public TournamentGroupTeamResponse addTeam(UUID groupId, AddTeamToGroupRequest request) {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        TournamentGroup group = findGroup(groupId);
        ensureCanManage(actor, group.tournamentId());
        Team team = teamRepository.findById(request.teamId())
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", request.teamId()));
        Integer seedNumber = normalizeSeedNumber(request.seedNumber());
        UUID registrationId = groupRepository.findApprovedRegistrationId(group.tournamentId(), team.id())
                .orElseThrow(() -> new ConflictException(
                        "Team must have an approved registration for this tournament before it can be added to a group."));

        ensureTeamNotAssigned(group, team.id());
        databaseActorContext.apply(actor);

        try {
            TournamentGroupTeam assignment = groupRepository.addTeam(new AddTournamentGroupTeamCommand(
                    group.id(),
                    team.id(),
                    registrationId,
                    seedNumber));

            return TournamentGroupTeamResponse.from(assignment);
        } catch (DataIntegrityViolationException exception) {
            throw groupConstraintException(exception);
        }
    }

    @Transactional
    public void removeTeam(UUID groupId, UUID teamId) {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        TournamentGroup group = findGroup(groupId);
        ensureCanManage(actor, group.tournamentId());
        databaseActorContext.apply(actor);

        groupRepository.removeTeam(groupId, teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament group team", "teamId", teamId));
    }

    @Transactional(readOnly = true)
    public List<GroupStandingResponse> getPublicStandings(UUID groupId) {
        ensurePublicGroupExists(groupId);

        return groupRepository.findStandingsByGroupId(groupId)
                .stream()
                .map(GroupStandingResponse::from)
                .toList();
    }

    private Tournament ensureTournamentExists(UUID tournamentId) {
        return tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));
    }

    private TournamentGroup findGroup(UUID groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament group", "id", groupId));
    }

    private void ensurePublicTournamentExists(UUID tournamentId) {
        if (!groupRepository.publicTournamentExists(tournamentId)) {
            throw new ResourceNotFoundException("Tournament", "id", tournamentId);
        }
    }

    private void ensurePublicGroupExists(UUID groupId) {
        if (!groupRepository.publicGroupExists(groupId)) {
            throw new ResourceNotFoundException("Tournament group", "id", groupId);
        }
    }

    private void ensureCanManage(AuthenticatedActor actor, UUID tournamentId) {
        UUID profileId = actor.requireProfileId();
        if (actor.isAdmin()) {
            return;
        }

        if (actor.role() == ProfileRole.ORGANIZER
                && tournamentRepository.canManage(tournamentId, profileId, false)) {
            return;
        }

        throw new AccessDeniedException("Only the tournament owner, tournament organizers, or admins can manage tournament groups.");
    }

    private void ensureTeamNotAssigned(TournamentGroup group, UUID teamId) {
        groupRepository.findAssignmentByTournamentAndTeam(group.tournamentId(), teamId)
                .ifPresent(existing -> {
                    if (existing.groupId().equals(group.id())) {
                        throw new ConflictException("Team is already assigned to this group.");
                    }

                    throw new ConflictException("Team is already assigned to another group in this tournament.");
                });
    }

    private String normalizeGroupName(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Tournament group name is required.");
        }

        return value.trim();
    }

    private Integer normalizeSeedNumber(Integer value) {
        if (value != null && value < 1) {
            throw new BadRequestException("Tournament group seed number must be positive.");
        }

        return value;
    }

    private ApiException groupConstraintException(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();

        if (message != null && message.contains("tournament_groups_tournament_id_name_key")) {
            return new ConflictException("Tournament group name is already in use for this tournament.");
        }

        if (message != null && message.contains("tournament_groups_tournament_id_sort_order_key")) {
            return new ConflictException("Tournament group sort order is already in use for this tournament.");
        }

        if (message != null && message.contains("tournament_group_teams_group_id_team_id_key")) {
            return new ConflictException("Team is already assigned to this group.");
        }

        if (message != null && message.contains("tournament_group_teams_group_id_seed_number_key")) {
            return new ConflictException("Tournament group seed number is already in use for this group.");
        }

        if (message != null && message.contains("tournament_group_teams_one_group_per_tournament")) {
            return new ConflictException("Team is already assigned to another group in this tournament.");
        }

        if (message != null && message.contains("tournament_group_teams_registration_approved")) {
            return new ConflictException("Team must have an approved registration for this tournament before it can be added to a group.");
        }

        if (message != null && message.contains("tournament_group_teams_registration_matches_group")) {
            return new BadRequestException("Tournament group team registration must belong to the same tournament and team.");
        }

        if (message != null && message.contains("tournament_groups_name_length")) {
            return new BadRequestException("Tournament group name must be between 1 and 80 characters.");
        }

        if (message != null && message.contains("tournament_groups_sort_positive")) {
            return new BadRequestException("Tournament group sort order must be positive.");
        }

        if (message != null && message.contains("tournament_group_teams_seed_positive")) {
            return new BadRequestException("Tournament group seed number must be positive.");
        }

        return new BadRequestException("Tournament group data violates a database constraint.");
    }
}
