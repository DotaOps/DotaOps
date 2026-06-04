package si.um.feri.dotaops.backend.team.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedProfile;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.ConflictException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamJoinRequestServiceTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CAPTAIN_PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID REQUESTER_PROFILE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID OTHER_PROFILE_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID TEAM_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID REQUEST_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-12T00:00:00Z");

    private final TeamJoinRequestRepository joinRequestRepository = mock(TeamJoinRequestRepository.class);
    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
    private final TeamManualPlayerRepository teamManualPlayerRepository = mock(TeamManualPlayerRepository.class);
    private final TeamInvitationRepository teamInvitationRepository = mock(TeamInvitationRepository.class);
    private final TeamRosterLimitRepository teamRosterLimitRepository = mock(TeamRosterLimitRepository.class);
    private final TeamRosterCapacityService teamRosterCapacityService = new TeamRosterCapacityService(
            teamMemberRepository,
            teamManualPlayerRepository,
            teamRosterLimitRepository);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final TeamJoinRequestService service = new TeamJoinRequestService(
            joinRequestRepository,
            teamRepository,
            teamMemberRepository,
            teamInvitationRepository,
            teamRosterCapacityService,
            currentUserProvider);

    @BeforeEach
    void setUpNormalizedCaptainMembership() {
        when(teamRepository.findByIdForUpdate(TEAM_ID)).thenReturn(Optional.of(team()));
        when(teamMemberRepository.existsActive(TEAM_ID, CAPTAIN_PROFILE_ID)).thenReturn(true);
    }

    @Test
    void playerCanCreateJoinRequest() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(currentUserProvider.requireProfile()).thenReturn(profile(REQUESTER_PROFILE_ID, ProfileRole.PLAYER));
        when(joinRequestRepository.findPendingByTeamAndRequester(TEAM_ID, REQUESTER_PROFILE_ID))
                .thenReturn(Optional.empty());
        when(teamInvitationRepository.findPendingByTeamAndInviteeProfile(TEAM_ID, REQUESTER_PROFILE_ID))
                .thenReturn(Optional.empty());
        when(teamRosterLimitRepository.resolveRosterLimit(TEAM_ID)).thenReturn(5);
        when(joinRequestRepository.create(any())).thenReturn(joinRequest(TeamJoinRequestStatus.PENDING));

        var response = service.createJoinRequest(TEAM_ID, new CreateTeamJoinRequestRequest("  I can play support.  "));

        ArgumentCaptor<CreateTeamJoinRequestCommand> captor = ArgumentCaptor.forClass(CreateTeamJoinRequestCommand.class);
        verify(joinRequestRepository).create(captor.capture());
        assertThat(response.status()).isEqualTo(TeamJoinRequestStatus.PENDING);
        assertThat(captor.getValue().message()).isEqualTo("I can play support.");
    }

    @Test
    void duplicatePendingRequestIsRejected() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(currentUserProvider.requireProfile()).thenReturn(profile(REQUESTER_PROFILE_ID, ProfileRole.PLAYER));
        when(joinRequestRepository.findPendingByTeamAndRequester(TEAM_ID, REQUESTER_PROFILE_ID))
                .thenReturn(Optional.of(joinRequest(TeamJoinRequestStatus.PENDING)));

        assertThatThrownBy(() -> service.createJoinRequest(TEAM_ID, new CreateTeamJoinRequestRequest(null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Pending join request already exists for this team.");

        verify(joinRequestRepository, never()).create(any());
    }

    @Test
    void activeMemberCannotCreateJoinRequest() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(currentUserProvider.requireProfile()).thenReturn(profile(REQUESTER_PROFILE_ID, ProfileRole.PLAYER));
        when(teamMemberRepository.existsActive(TEAM_ID, REQUESTER_PROFILE_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.createJoinRequest(TEAM_ID, new CreateTeamJoinRequestRequest(null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Profile is already an active team member.");
    }

    @Test
    void captainCanViewTeamJoinRequests() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(currentUserProvider.requireProfile()).thenReturn(profile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(joinRequestRepository.findByTeamId(TEAM_ID, TeamJoinRequestStatus.PENDING))
                .thenReturn(List.of(joinRequest(TeamJoinRequestStatus.PENDING)));

        var response = service.listTeamJoinRequests(TEAM_ID, "pending");

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().requesterProfileId()).isEqualTo(REQUESTER_PROFILE_ID);
    }

    @Test
    void nonCaptainCannotViewOtherTeamJoinRequests() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(currentUserProvider.requireProfile()).thenReturn(profile(OTHER_PROFILE_ID, ProfileRole.PLAYER));

        assertThatThrownBy(() -> service.listTeamJoinRequests(TEAM_ID, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only the team captain or an admin can view join requests.");
    }

    @Test
    void requesterCanViewOwnJoinRequests() {
        when(currentUserProvider.requireProfile()).thenReturn(profile(REQUESTER_PROFILE_ID, ProfileRole.PLAYER));
        when(joinRequestRepository.findByRequesterProfileId(REQUESTER_PROFILE_ID, null))
                .thenReturn(List.of(joinRequest(TeamJoinRequestStatus.PENDING)));

        var response = service.listCurrentUserJoinRequests(null);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().teamId()).isEqualTo(TEAM_ID);
    }

    @Test
    void organizerCannotViewTeamJoinRequests() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(currentUserProvider.requireProfile()).thenReturn(profile(OTHER_PROFILE_ID, ProfileRole.ORGANIZER));

        assertThatThrownBy(() -> service.listTeamJoinRequests(TEAM_ID, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only the team captain or an admin can view join requests.");
    }

    @Test
    void organizerCannotCreateJoinRequest() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(currentUserProvider.requireProfile()).thenReturn(profile(OTHER_PROFILE_ID, ProfileRole.ORGANIZER));

        assertThatThrownBy(() -> service.createJoinRequest(TEAM_ID, new CreateTeamJoinRequestRequest(null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only players can request team membership.");

        verify(joinRequestRepository, never()).create(any());
    }

    @Test
    void captainCanAcceptJoinRequestAndMemberIsCreated() {
        when(joinRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(joinRequest(TeamJoinRequestStatus.PENDING)));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(currentUserProvider.requireProfile()).thenReturn(profile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRosterLimitRepository.resolveRosterLimit(TEAM_ID)).thenReturn(5);
        when(joinRequestRepository.accept(REQUEST_ID, CAPTAIN_PROFILE_ID))
                .thenReturn(Optional.of(joinRequest(TeamJoinRequestStatus.ACCEPTED)));

        var response = service.acceptJoinRequest(REQUEST_ID);

        ArgumentCaptor<CreateTeamMemberCommand> captor = ArgumentCaptor.forClass(CreateTeamMemberCommand.class);
        verify(teamMemberRepository).create(captor.capture());
        assertThat(captor.getValue().profileId()).isEqualTo(REQUESTER_PROFILE_ID);
        assertThat(captor.getValue().role()).isEqualTo(TeamMemberRole.SUPPORT);
        assertThat(response.status()).isEqualTo(TeamJoinRequestStatus.ACCEPTED);
    }

    @Test
    void acceptRejectsFullTeam() {
        when(joinRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(joinRequest(TeamJoinRequestStatus.PENDING)));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(currentUserProvider.requireProfile()).thenReturn(profile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRosterLimitRepository.resolveRosterLimit(TEAM_ID)).thenReturn(1);
        when(teamManualPlayerRepository.countByTeamId(TEAM_ID)).thenReturn(1);

        assertThatThrownBy(() -> service.acceptJoinRequest(REQUEST_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Team roster is full.");

        verify(teamMemberRepository, never()).create(any());
    }

    @Test
    void captainCanDeclineJoinRequest() {
        when(joinRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(joinRequest(TeamJoinRequestStatus.PENDING)));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(currentUserProvider.requireProfile()).thenReturn(profile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(joinRequestRepository.decline(REQUEST_ID, CAPTAIN_PROFILE_ID))
                .thenReturn(Optional.of(joinRequest(TeamJoinRequestStatus.DECLINED)));

        var response = service.declineJoinRequest(REQUEST_ID);

        assertThat(response.status()).isEqualTo(TeamJoinRequestStatus.DECLINED);
    }

    @Test
    void requesterCanCancelJoinRequest() {
        when(joinRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(joinRequest(TeamJoinRequestStatus.PENDING)));
        when(currentUserProvider.requireProfile()).thenReturn(profile(REQUESTER_PROFILE_ID, ProfileRole.PLAYER));
        when(joinRequestRepository.cancel(REQUEST_ID, REQUESTER_PROFILE_ID))
                .thenReturn(Optional.of(joinRequest(TeamJoinRequestStatus.CANCELLED)));

        var response = service.cancelJoinRequest(REQUEST_ID);

        assertThat(response.status()).isEqualTo(TeamJoinRequestStatus.CANCELLED);
    }

    @Test
    void otherUserCannotCancelJoinRequest() {
        when(joinRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(joinRequest(TeamJoinRequestStatus.PENDING)));
        when(currentUserProvider.requireProfile()).thenReturn(profile(OTHER_PROFILE_ID, ProfileRole.PLAYER));

        assertThatThrownBy(() -> service.cancelJoinRequest(REQUEST_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only the requester can cancel this join request.");
    }

    private static Team team() {
        return new Team(
                TEAM_ID,
                "Ancient Stack",
                "AS",
                "ancient-stack",
                CAPTAIN_PROFILE_ID,
                "Captain",
                "EU",
                null,
                null,
                AUTH_USER_ID,
                NOW,
                NOW);
    }

    private static AuthenticatedProfile profile(UUID profileId, ProfileRole role) {
        return new AuthenticatedProfile(
                profileId,
                AUTH_USER_ID,
                "Player",
                role);
    }

    private static TeamJoinRequest joinRequest(TeamJoinRequestStatus status) {
        return new TeamJoinRequest(
                REQUEST_ID,
                TEAM_ID,
                "Ancient Stack",
                "ancient-stack",
                REQUESTER_PROFILE_ID,
                "Requester",
                "Ready",
                status,
                NOW,
                NOW,
                status == TeamJoinRequestStatus.PENDING ? null : NOW,
                status == TeamJoinRequestStatus.PENDING ? null : CAPTAIN_PROFILE_ID,
                status == TeamJoinRequestStatus.PENDING ? null : "Captain");
    }
}
