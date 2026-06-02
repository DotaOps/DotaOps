package si.um.feri.dotaops.backend.team.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import si.um.feri.dotaops.backend.analytics.service.AnalyticsQueryService;
import si.um.feri.dotaops.backend.auth.domain.AuthenticatedProfile;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.auth.service.SupabasePrincipal;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.security.DatabaseActorContext;
import si.um.feri.dotaops.backend.profile.domain.Profile;
import si.um.feri.dotaops.backend.profile.repository.ProfileRepository;
import si.um.feri.dotaops.backend.team.domain.Team;
import si.um.feri.dotaops.backend.team.domain.TeamInvitation;
import si.um.feri.dotaops.backend.team.domain.TeamInvitationStatus;
import si.um.feri.dotaops.backend.team.domain.TeamManualPlayer;
import si.um.feri.dotaops.backend.team.domain.TeamMember;
import si.um.feri.dotaops.backend.team.domain.TeamMemberRole;
import si.um.feri.dotaops.backend.team.repository.CreateTeamInvitationCommand;
import si.um.feri.dotaops.backend.team.repository.CreateTeamMemberCommand;
import si.um.feri.dotaops.backend.team.repository.CreateTeamManualPlayerCommand;
import si.um.feri.dotaops.backend.team.repository.TeamManualPlayerRepository;
import si.um.feri.dotaops.backend.team.repository.TeamInvitationRepository;
import si.um.feri.dotaops.backend.team.repository.TeamJoinRequestRepository;
import si.um.feri.dotaops.backend.team.repository.TeamMemberRepository;
import si.um.feri.dotaops.backend.team.repository.TeamRepository;
import si.um.feri.dotaops.backend.team.repository.TeamRosterLimitRepository;
import si.um.feri.dotaops.backend.team.web.AddTeamMemberRequest;
import si.um.feri.dotaops.backend.team.web.CreateTeamInvitationRequest;
import si.um.feri.dotaops.backend.team.web.CreateTeamManualPlayerRequest;
import si.um.feri.dotaops.backend.team.web.TransferTeamOwnershipRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamRosterServiceTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CAPTAIN_PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID INVITEE_PROFILE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID OTHER_PROFILE_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID TEAM_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID MEMBER_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID INVITATION_ID = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID OWNER_MEMBER_ID = UUID.fromString("99999999-9999-4999-8999-999999999999");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-12T00:00:00Z");

    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
    private final TeamManualPlayerRepository teamManualPlayerRepository = mock(TeamManualPlayerRepository.class);
    private final TeamInvitationRepository teamInvitationRepository = mock(TeamInvitationRepository.class);
    private final TeamJoinRequestRepository teamJoinRequestRepository = mock(TeamJoinRequestRepository.class);
    private final TeamRosterLimitRepository teamRosterLimitRepository = mock(TeamRosterLimitRepository.class);
    private final TeamRosterCapacityService teamRosterCapacityService = new TeamRosterCapacityService(
            teamMemberRepository,
            teamManualPlayerRepository,
            teamRosterLimitRepository);
    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final AnalyticsQueryService analyticsQueryService = mock(AnalyticsQueryService.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final DatabaseActorContext databaseActorContext = mock(DatabaseActorContext.class);
    private final TeamRosterService teamRosterService = new TeamRosterService(
            teamRepository,
            teamMemberRepository,
            teamManualPlayerRepository,
            teamInvitationRepository,
            teamJoinRequestRepository,
            teamRosterCapacityService,
            analyticsQueryService,
            profileRepository,
            currentUserProvider,
            databaseActorContext);

    @BeforeEach
    void setUpNormalizedCaptainMembership() {
        when(teamRepository.findByIdForUpdate(TEAM_ID)).thenReturn(Optional.of(team()));
        when(teamMemberRepository.existsActive(TEAM_ID, CAPTAIN_PROFILE_ID)).thenReturn(true);
    }

    @Test
    void currentTeamUsesCurrentProfileAndReturnsRosterPermissions() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.findCurrentTeamForProfile(CAPTAIN_PROFILE_ID)).thenReturn(Optional.of(team()));
        when(teamMemberRepository.findActiveByTeamId(TEAM_ID)).thenReturn(List.of(member(true, TeamMemberRole.MID)));

        var response = teamRosterService.getCurrentTeam();

        assertThat(response.team().id()).isEqualTo(TEAM_ID);
        assertThat(response.members()).hasSize(1);
        assertThat(response.captain()).isTrue();
        assertThat(response.isTeamOwner()).isTrue();
        assertThat(response.currentUserTeamRole()).isEqualTo("owner");
        assertThat(response.canManageTeam()).isTrue();
        assertThat(response.canManageRoster()).isTrue();
        assertThat(response.canInvitePlayers()).isTrue();
        assertThat(response.canTransferOwnership()).isTrue();
        assertThat(response.canLeaveTeam()).isFalse();
        assertThat(response.canDisbandTeam()).isTrue();
        assertThat(response.canViewAnalytics()).isTrue();
    }

    @Test
    void currentTeamReturnsEmptyResponseWhenProfileHasNoTeam() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(OTHER_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.findCurrentTeamForProfile(OTHER_PROFILE_ID)).thenReturn(Optional.empty());

        var response = teamRosterService.getCurrentTeam();

        assertThat(response.team()).isNull();
        assertThat(response.members()).isEmpty();
        assertThat(response.captain()).isFalse();
        assertThat(response.canCreateTeam()).isTrue();
        assertThat(response.canManageRoster()).isFalse();
        assertThat(response.canLeaveTeam()).isFalse();
        assertThat(response.canDisbandTeam()).isFalse();
    }

    @Test
    void soloOwnerCannotTransferOwnershipWithoutAnotherActiveMember() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.findCurrentTeamForProfile(CAPTAIN_PROFILE_ID)).thenReturn(Optional.of(team()));
        when(teamMemberRepository.findActiveByTeamId(TEAM_ID)).thenReturn(List.of(
                member(OWNER_MEMBER_ID, CAPTAIN_PROFILE_ID, true, TeamMemberRole.SUPPORT)));

        var response = teamRosterService.getCurrentTeam();

        assertThat(response.canTransferOwnership()).isFalse();
        assertThat(response.canLeaveTeam()).isFalse();
        assertThat(response.canDisbandTeam()).isTrue();
    }

    @Test
    void normalMemberCanLeaveCurrentTeamAndKeepsHistoricalMembership() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(INVITEE_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.findCurrentTeamForProfile(INVITEE_PROFILE_ID)).thenReturn(Optional.of(team()));
        when(teamMemberRepository.findActiveByTeamAndProfile(TEAM_ID, INVITEE_PROFILE_ID))
                .thenReturn(Optional.of(member(true, TeamMemberRole.MID)));
        when(teamMemberRepository.deactivate(TEAM_ID, MEMBER_ID))
                .thenReturn(Optional.of(member(false, TeamMemberRole.MID)));

        var response = teamRosterService.leaveCurrentTeam();

        assertThat(response.team()).isNull();
        assertThat(response.canCreateTeam()).isTrue();
        assertThat(response.canLeaveTeam()).isFalse();
        verify(databaseActorContext).apply(any());
        verify(teamMemberRepository).deactivate(TEAM_ID, MEMBER_ID);
    }

    @Test
    void ownerMustTransferOwnershipOrDisbandBeforeLeaving() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.findCurrentTeamForProfile(CAPTAIN_PROFILE_ID)).thenReturn(Optional.of(team()));

        assertThatThrownBy(() -> teamRosterService.leaveCurrentTeam())
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Team owner must transfer ownership or disband the team before leaving.");

        verify(teamMemberRepository, never()).deactivate(any(), any());
    }

    @Test
    void ownerCanSoftDisbandTeamAndCloseOpenMembershipFlows() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.disband(TEAM_ID)).thenReturn(Optional.of(NOW));

        var response = teamRosterService.disbandTeam(TEAM_ID);

        assertThat(response.teamId()).isEqualTo(TEAM_ID);
        assertThat(response.status()).isEqualTo("disbanded");
        assertThat(response.disbandedAt()).isEqualTo(NOW);
        verify(databaseActorContext).apply(any());
        verify(teamMemberRepository).deactivateAllActiveByTeamId(TEAM_ID);
        verify(teamInvitationRepository).cancelPendingByTeamId(TEAM_ID);
        verify(teamJoinRequestRepository).cancelPendingByTeamId(TEAM_ID, CAPTAIN_PROFILE_ID);
    }

    @Test
    void normalMemberCannotDisbandTeam() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(INVITEE_PROFILE_ID, ProfileRole.PLAYER));

        assertThatThrownBy(() -> teamRosterService.disbandTeam(TEAM_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only the current team owner can disband this team.");

        verify(teamRepository, never()).disband(TEAM_ID);
    }

    @Test
    void activeMemberCanReadRosterProfileWithStableEmptyStats() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(INVITEE_PROFILE_ID, ProfileRole.PLAYER));
        when(teamMemberRepository.existsActive(TEAM_ID, INVITEE_PROFILE_ID)).thenReturn(true);
        when(teamMemberRepository.findActiveByTeamAndProfile(TEAM_ID, INVITEE_PROFILE_ID))
                .thenReturn(Optional.of(member(true, TeamMemberRole.MID)));
        when(analyticsQueryService.protectedPlayerMetrics(any())).thenReturn(List.of());
        when(analyticsQueryService.protectedHeroMetrics(any())).thenReturn(List.of());

        var response = teamRosterService.getRosterProfile(TEAM_ID, INVITEE_PROFILE_ID);

        assertThat(response.profileId()).isEqualTo(INVITEE_PROFILE_ID);
        assertThat(response.role()).isEqualTo(TeamMemberRole.MID);
        assertThat(response.teamOwner()).isFalse();
        assertThat(response.stats().gamesPlayed()).isZero();
        assertThat(response.stats().winRate()).isEqualByComparingTo("0.00");
        assertThat(response.mostPlayedHeroes()).isEmpty();
        assertThat(response.recentMatches()).isEmpty();
    }

    @Test
    void foreignPlayerCannotReadPrivateRosterProfile() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(OTHER_PROFILE_ID, ProfileRole.PLAYER));

        assertThatThrownBy(() -> teamRosterService.getRosterProfile(TEAM_ID, INVITEE_PROFILE_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only active team members can view private roster profiles.");
    }

    @Test
    void captainCanAddMemberToOwnTeam() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(profileRepository.findById(INVITEE_PROFILE_ID)).thenReturn(Optional.of(profile(INVITEE_PROFILE_ID)));
        when(teamMemberRepository.create(any())).thenReturn(member(true, TeamMemberRole.MID));

        teamRosterService.addMember(TEAM_ID, new AddTeamMemberRequest(INVITEE_PROFILE_ID, TeamMemberRole.MID));

        ArgumentCaptor<CreateTeamMemberCommand> captor = ArgumentCaptor.forClass(CreateTeamMemberCommand.class);
        verify(teamMemberRepository).create(captor.capture());

        assertThat(captor.getValue().teamId()).isEqualTo(TEAM_ID);
        assertThat(captor.getValue().profileId()).isEqualTo(INVITEE_PROFILE_ID);
        assertThat(captor.getValue().role()).isEqualTo(TeamMemberRole.MID);
    }

    @Test
    void nonCaptainCannotAddMember() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(OTHER_PROFILE_ID, ProfileRole.PLAYER));

        assertThatThrownBy(() -> teamRosterService.addMember(
                TEAM_ID,
                new AddTeamMemberRequest(INVITEE_PROFILE_ID, TeamMemberRole.SUPPORT)))
                .isInstanceOf(AccessDeniedException.class);

        verify(teamMemberRepository, never()).create(any());
    }

    @Test
    void organizerCannotAddMember() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(OTHER_PROFILE_ID, ProfileRole.ORGANIZER));

        assertThatThrownBy(() -> teamRosterService.addMember(
                TEAM_ID,
                new AddTeamMemberRequest(INVITEE_PROFILE_ID, TeamMemberRole.SUPPORT)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only the team captain or an admin can manage this team.");

        verify(teamMemberRepository, never()).create(any());
    }

    @Test
    void deactivateMemberKeepsHistoricalRow() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member(true, TeamMemberRole.SUPPORT)));
        when(teamMemberRepository.deactivate(TEAM_ID, MEMBER_ID)).thenReturn(Optional.of(member(false, TeamMemberRole.SUPPORT)));

        var response = teamRosterService.deactivateMember(TEAM_ID, MEMBER_ID);

        assertThat(response.active()).isFalse();
        assertThat(response.leftAt()).isEqualTo(NOW);
    }

    @Test
    void currentOwnerCannotBeRemovedBeforeOwnershipTransfer() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamMemberRepository.findById(OWNER_MEMBER_ID))
                .thenReturn(Optional.of(member(OWNER_MEMBER_ID, CAPTAIN_PROFILE_ID, true, TeamMemberRole.SUPPORT)));

        assertThatThrownBy(() -> teamRosterService.deactivateMember(TEAM_ID, OWNER_MEMBER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Transfer ownership before removing the current team owner.");

        verify(teamMemberRepository, never()).deactivate(TEAM_ID, OWNER_MEMBER_ID);
    }

    @Test
    void captainCanCreateManualPlayerWhenRosterHasRoom() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRosterLimitRepository.resolveRosterLimit(TEAM_ID)).thenReturn(3);
        when(teamMemberRepository.countActiveByTeamId(TEAM_ID)).thenReturn(2);
        when(teamManualPlayerRepository.create(any())).thenReturn(manualPlayer());

        var response = teamRosterService.createManualPlayer(
                TEAM_ID,
                new CreateTeamManualPlayerRequest("  Guest Mid  ", " guest ", "  local player  "));

        ArgumentCaptor<CreateTeamManualPlayerCommand> captor = ArgumentCaptor.forClass(CreateTeamManualPlayerCommand.class);
        verify(teamManualPlayerRepository).create(captor.capture());

        assertThat(response.displayName()).isEqualTo("Guest Mid");
        assertThat(captor.getValue().displayName()).isEqualTo("Guest Mid");
        assertThat(captor.getValue().nickname()).isEqualTo("guest");
        assertThat(captor.getValue().note()).isEqualTo("local player");
    }

    @Test
    void manualPlayerCountsAgainstRosterLimit() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRosterLimitRepository.resolveRosterLimit(TEAM_ID)).thenReturn(1);
        when(teamMemberRepository.countActiveByTeamId(TEAM_ID)).thenReturn(1);

        assertThatThrownBy(() -> teamRosterService.createManualPlayer(
                TEAM_ID,
                new CreateTeamManualPlayerRequest("Guest", null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Team roster cannot exceed 1 players.");

        verify(teamManualPlayerRepository, never()).create(any());
    }

    @Test
    void nonCaptainCannotCreateManualPlayer() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(OTHER_PROFILE_ID, ProfileRole.PLAYER));

        assertThatThrownBy(() -> teamRosterService.createManualPlayer(
                TEAM_ID,
                new CreateTeamManualPlayerRequest("Guest", null, null)))
                .isInstanceOf(AccessDeniedException.class);

        verify(teamManualPlayerRepository, never()).create(any());
    }

    @Test
    void createInvitationRejectsDuplicatePendingProfileInvitation() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(profileRepository.findById(INVITEE_PROFILE_ID)).thenReturn(Optional.of(profile(INVITEE_PROFILE_ID)));
        when(teamInvitationRepository.findPendingByTeamAndInviteeProfile(TEAM_ID, INVITEE_PROFILE_ID))
                .thenReturn(Optional.of(invitation(TeamInvitationStatus.PENDING, INVITEE_PROFILE_ID, null, null)));

        assertThatThrownBy(() -> teamRosterService.createInvitation(
                TEAM_ID,
                new CreateTeamInvitationRequest(INVITEE_PROFILE_ID, null, TeamMemberRole.CARRY, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Pending invitation already exists for this team and profile.");

        verify(teamInvitationRepository, never()).create(any(CreateTeamInvitationCommand.class));
    }

    @Test
    void createInvitationAllowsMatchingProfileAndEmail() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(profileRepository.findById(INVITEE_PROFILE_ID)).thenReturn(Optional.of(profile(INVITEE_PROFILE_ID)));
        when(profileRepository.emailMatchesProfileAuthUser(INVITEE_PROFILE_ID, "player@example.com")).thenReturn(true);
        when(teamInvitationRepository.create(any()))
                .thenReturn(invitation(TeamInvitationStatus.PENDING, INVITEE_PROFILE_ID, "player@example.com", null));

        teamRosterService.createInvitation(
                TEAM_ID,
                new CreateTeamInvitationRequest(INVITEE_PROFILE_ID, "Player@Example.com", TeamMemberRole.CARRY, null));

        ArgumentCaptor<CreateTeamInvitationCommand> captor = ArgumentCaptor.forClass(CreateTeamInvitationCommand.class);
        verify(teamInvitationRepository).create(captor.capture());

        assertThat(captor.getValue().inviteeProfileId()).isEqualTo(INVITEE_PROFILE_ID);
        assertThat(captor.getValue().inviteeEmail()).isEqualTo("player@example.com");
    }

    @Test
    void createInvitationRejectsProfileAndEmailForDifferentUsers() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(profileRepository.findById(INVITEE_PROFILE_ID)).thenReturn(Optional.of(profile(INVITEE_PROFILE_ID)));
        when(profileRepository.emailMatchesProfileAuthUser(INVITEE_PROFILE_ID, "other@example.com")).thenReturn(false);

        assertThatThrownBy(() -> teamRosterService.createInvitation(
                TEAM_ID,
                new CreateTeamInvitationRequest(INVITEE_PROFILE_ID, "other@example.com", TeamMemberRole.CARRY, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invitee profile id and invitee email must reference the same user.");

        verify(teamInvitationRepository, never()).create(any(CreateTeamInvitationCommand.class));
    }

    @Test
    void invitedUserAcceptsInvitationAndBecomesMember() {
        when(teamInvitationRepository.findById(INVITATION_ID))
                .thenReturn(Optional.of(invitation(TeamInvitationStatus.PENDING, INVITEE_PROFILE_ID, null, null)));
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(INVITEE_PROFILE_ID, ProfileRole.PLAYER));
        when(currentUserProvider.currentUser()).thenReturn(Optional.of(principal(INVITEE_PROFILE_ID, "player@example.com")));
        when(teamInvitationRepository.accept(INVITATION_ID, INVITEE_PROFILE_ID))
                .thenReturn(Optional.of(invitation(TeamInvitationStatus.ACCEPTED, INVITEE_PROFILE_ID, null, NOW)));
        when(teamMemberRepository.create(any())).thenReturn(member(true, TeamMemberRole.CARRY));

        var response = teamRosterService.acceptInvitation(INVITATION_ID);

        ArgumentCaptor<CreateTeamMemberCommand> captor = ArgumentCaptor.forClass(CreateTeamMemberCommand.class);
        verify(teamMemberRepository).create(captor.capture());

        assertThat(response.status()).isEqualTo(TeamInvitationStatus.ACCEPTED);
        assertThat(captor.getValue().profileId()).isEqualTo(INVITEE_PROFILE_ID);
        assertThat(captor.getValue().role()).isEqualTo(TeamMemberRole.CARRY);
    }

    @Test
    void createInvitationAllowsPlayerEmailWithoutProfileId() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamInvitationRepository.create(any()))
                .thenReturn(invitation(TeamInvitationStatus.PENDING, null, "player@example.com", null));

        teamRosterService.createInvitation(
                TEAM_ID,
                new CreateTeamInvitationRequest(null, "Player@Example.com", TeamMemberRole.CARRY, null));

        ArgumentCaptor<CreateTeamInvitationCommand> captor = ArgumentCaptor.forClass(CreateTeamInvitationCommand.class);
        verify(teamInvitationRepository).create(captor.capture());

        assertThat(captor.getValue().inviteeProfileId()).isNull();
        assertThat(captor.getValue().inviteeEmail()).isEqualTo("player@example.com");
    }

    @Test
    void createInvitationRejectsFullTeam() {
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team()));
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRosterLimitRepository.resolveRosterLimit(TEAM_ID)).thenReturn(5);
        when(teamMemberRepository.countActiveByTeamId(TEAM_ID)).thenReturn(5);

        assertThatThrownBy(() -> teamRosterService.createInvitation(
                TEAM_ID,
                new CreateTeamInvitationRequest(null, "player@example.com", TeamMemberRole.CARRY, null)))
                .isInstanceOf(si.um.feri.dotaops.backend.common.error.ConflictException.class)
                .hasMessage("Team roster is full.");

        verify(teamInvitationRepository, never()).create(any());
    }

    @Test
    void invitedUserCannotAcceptInvitationWhenRosterBecameFull() {
        when(teamInvitationRepository.findById(INVITATION_ID))
                .thenReturn(Optional.of(invitation(TeamInvitationStatus.PENDING, INVITEE_PROFILE_ID, null, null)));
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(INVITEE_PROFILE_ID, ProfileRole.PLAYER));
        when(currentUserProvider.currentUser()).thenReturn(Optional.of(principal(INVITEE_PROFILE_ID, "player@example.com")));
        when(teamRosterLimitRepository.resolveRosterLimit(TEAM_ID)).thenReturn(1);
        when(teamMemberRepository.countActiveByTeamId(TEAM_ID)).thenReturn(1);

        assertThatThrownBy(() -> teamRosterService.acceptInvitation(INVITATION_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Team roster cannot exceed 1 players.");

        verify(teamInvitationRepository, never()).accept(eq(INVITATION_ID), any());
        verify(teamMemberRepository, never()).create(any());
    }

    @Test
    void invitedUserCannotAcceptInvitationWhenAlreadyOnAnotherTeam() {
        when(teamInvitationRepository.findById(INVITATION_ID))
                .thenReturn(Optional.of(invitation(TeamInvitationStatus.PENDING, INVITEE_PROFILE_ID, null, null)));
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(INVITEE_PROFILE_ID, ProfileRole.PLAYER));
        when(currentUserProvider.currentUser()).thenReturn(Optional.of(principal(INVITEE_PROFILE_ID, "player@example.com")));
        when(teamRepository.existsCurrentTeamForProfileExcluding(INVITEE_PROFILE_ID, TEAM_ID)).thenReturn(true);

        assertThatThrownBy(() -> teamRosterService.acceptInvitation(INVITATION_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Profile already belongs to another active team.");

        verify(teamInvitationRepository, never()).accept(eq(INVITATION_ID), any());
        verify(teamMemberRepository, never()).create(any());
    }

    @Test
    void organizerCannotAcceptPlayerInvitation() {
        when(teamInvitationRepository.findById(INVITATION_ID))
                .thenReturn(Optional.of(invitation(TeamInvitationStatus.PENDING, INVITEE_PROFILE_ID, null, null)));
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(INVITEE_PROFILE_ID, ProfileRole.ORGANIZER));

        assertThatThrownBy(() -> teamRosterService.acceptInvitation(INVITATION_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only players can respond to team invitations.");

        verify(teamInvitationRepository, never()).accept(eq(INVITATION_ID), any());
        verify(teamMemberRepository, never()).create(any());
    }

    @Test
    void unrelatedUserCannotAcceptInvitation() {
        when(teamInvitationRepository.findById(INVITATION_ID))
                .thenReturn(Optional.of(invitation(TeamInvitationStatus.PENDING, INVITEE_PROFILE_ID, null, null)));
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(OTHER_PROFILE_ID, ProfileRole.PLAYER));
        when(currentUserProvider.currentUser()).thenReturn(Optional.of(principal(OTHER_PROFILE_ID, "other@example.com")));

        assertThatThrownBy(() -> teamRosterService.acceptInvitation(INVITATION_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(teamMemberRepository, never()).create(any());
    }

    @Test
    void acceptedInvitationCannotBeAcceptedAgain() {
        when(teamInvitationRepository.findById(INVITATION_ID))
                .thenReturn(Optional.of(invitation(TeamInvitationStatus.ACCEPTED, INVITEE_PROFILE_ID, null, NOW)));
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(INVITEE_PROFILE_ID, ProfileRole.PLAYER));
        when(currentUserProvider.currentUser()).thenReturn(Optional.of(principal(INVITEE_PROFILE_ID, "player@example.com")));

        assertThatThrownBy(() -> teamRosterService.acceptInvitation(INVITATION_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only pending invitations can be changed.");

        verify(teamInvitationRepository, never()).accept(eq(INVITATION_ID), any());
        verify(teamMemberRepository, never()).create(any());
    }

    @Test
    void matchingProfileCannotAcceptInvitationWithDifferentEmailWhenBothAreSet() {
        when(teamInvitationRepository.findById(INVITATION_ID))
                .thenReturn(Optional.of(invitation(
                        TeamInvitationStatus.PENDING,
                        INVITEE_PROFILE_ID,
                        "player@example.com",
                        null)));
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(INVITEE_PROFILE_ID, ProfileRole.PLAYER));
        when(currentUserProvider.currentUser()).thenReturn(Optional.of(principal(INVITEE_PROFILE_ID, "other@example.com")));

        assertThatThrownBy(() -> teamRosterService.acceptInvitation(INVITATION_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(teamInvitationRepository, never()).accept(eq(INVITATION_ID), any());
        verify(teamMemberRepository, never()).create(any());
    }

    @Test
    void matchingEmailCannotAcceptInvitationForDifferentProfileWhenBothAreSet() {
        when(teamInvitationRepository.findById(INVITATION_ID))
                .thenReturn(Optional.of(invitation(
                        TeamInvitationStatus.PENDING,
                        INVITEE_PROFILE_ID,
                        "player@example.com",
                        null)));
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(OTHER_PROFILE_ID, ProfileRole.PLAYER));
        when(currentUserProvider.currentUser()).thenReturn(Optional.of(principal(OTHER_PROFILE_ID, "player@example.com")));

        assertThatThrownBy(() -> teamRosterService.acceptInvitation(INVITATION_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(teamInvitationRepository, never()).accept(eq(INVITATION_ID), any());
        verify(teamMemberRepository, never()).create(any());
    }

    @Test
    void expiredInvitationIsMarkedExpiredBeforeRejectingResponse() {
        OffsetDateTime past = OffsetDateTime.parse("2020-01-01T00:00:00Z");
        when(teamInvitationRepository.findById(INVITATION_ID))
                .thenReturn(Optional.of(invitation(TeamInvitationStatus.PENDING, INVITEE_PROFILE_ID, null, null, past)));
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(INVITEE_PROFILE_ID, ProfileRole.PLAYER));

        assertThatThrownBy(() -> teamRosterService.acceptInvitation(INVITATION_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Team invitation has expired.");

        verify(teamInvitationRepository).expire(INVITATION_ID);
        verify(teamMemberRepository, never()).create(any());
    }

    @Test
    void ownerCanTransferOwnershipToActivePlayerMember() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(profileRepository.findById(INVITEE_PROFILE_ID)).thenReturn(Optional.of(profile(INVITEE_PROFILE_ID)));
        when(teamMemberRepository.existsActive(TEAM_ID, INVITEE_PROFILE_ID)).thenReturn(true);
        when(teamRepository.transferOwnership(TEAM_ID, INVITEE_PROFILE_ID)).thenReturn(Optional.of(team(INVITEE_PROFILE_ID)));
        when(teamMemberRepository.findActiveByTeamId(TEAM_ID)).thenReturn(List.of(
                member(OWNER_MEMBER_ID, CAPTAIN_PROFILE_ID, true, TeamMemberRole.SUPPORT),
                member(true, TeamMemberRole.MID)));

        var response = teamRosterService.transferOwnership(
                TEAM_ID,
                new TransferTeamOwnershipRequest(INVITEE_PROFILE_ID));

        assertThat(response.team().captainProfileId()).isEqualTo(INVITEE_PROFILE_ID);
        assertThat(response.isTeamOwner()).isFalse();
        assertThat(response.canManageRoster()).isFalse();
        assertThat(response.canInvitePlayers()).isFalse();
        assertThat(response.canTransferOwnership()).isFalse();
        assertThat(response.members()).hasSize(2);
        assertThat(response.members()).anySatisfy(member -> {
            assertThat(member.profileId()).isEqualTo(CAPTAIN_PROFILE_ID);
            assertThat(member.teamOwner()).isFalse();
        });
        assertThat(response.members()).anySatisfy(member -> {
            assertThat(member.profileId()).isEqualTo(INVITEE_PROFILE_ID);
            assertThat(member.teamOwner()).isTrue();
        });
        verify(databaseActorContext).apply(any());
    }

    @Test
    void newOwnerReceivesOwnerCapabilitiesFromCurrentTeamResponse() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(INVITEE_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.findCurrentTeamForProfile(INVITEE_PROFILE_ID)).thenReturn(Optional.of(team(INVITEE_PROFILE_ID)));
        when(teamMemberRepository.existsActive(TEAM_ID, INVITEE_PROFILE_ID)).thenReturn(true);
        when(teamMemberRepository.findActiveByTeamId(TEAM_ID)).thenReturn(List.of(
                member(OWNER_MEMBER_ID, CAPTAIN_PROFILE_ID, true, TeamMemberRole.SUPPORT),
                member(true, TeamMemberRole.MID)));

        var response = teamRosterService.getCurrentTeam();

        assertThat(response.isTeamOwner()).isTrue();
        assertThat(response.canManageRoster()).isTrue();
        assertThat(response.canInvitePlayers()).isTrue();
        assertThat(response.canTransferOwnership()).isTrue();
        assertThat(response.canLeaveTeam()).isFalse();
        assertThat(response.canDisbandTeam()).isTrue();
        assertThat(response.currentUserTeamRole()).isEqualTo("owner");
        assertThat(response.members()).anySatisfy(member -> {
            assertThat(member.profileId()).isEqualTo(INVITEE_PROFILE_ID);
            assertThat(member.teamOwner()).isTrue();
        });
    }

    @Test
    void nonOwnerCannotTransferOwnership() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(OTHER_PROFILE_ID, ProfileRole.PLAYER));

        assertThatThrownBy(() -> teamRosterService.transferOwnership(
                TEAM_ID,
                new TransferTeamOwnershipRequest(INVITEE_PROFILE_ID)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only the current team owner can transfer ownership.");
    }

    @Test
    void organizerCannotTransferOwnership() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(CAPTAIN_PROFILE_ID, ProfileRole.ORGANIZER));

        assertThatThrownBy(() -> teamRosterService.transferOwnership(
                TEAM_ID,
                new TransferTeamOwnershipRequest(INVITEE_PROFILE_ID)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only players can transfer team ownership.");
    }

    @Test
    void adminDoesNotReceiveImplicitOwnershipTransferBypass() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(CAPTAIN_PROFILE_ID, ProfileRole.ADMIN));

        assertThatThrownBy(() -> teamRosterService.transferOwnership(
                TEAM_ID,
                new TransferTeamOwnershipRequest(INVITEE_PROFILE_ID)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only players can transfer team ownership.");
    }

    @Test
    void ownerCannotTransferOwnershipToNonMember() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(profileRepository.findById(INVITEE_PROFILE_ID)).thenReturn(Optional.of(profile(INVITEE_PROFILE_ID)));

        assertThatThrownBy(() -> teamRosterService.transferOwnership(
                TEAM_ID,
                new TransferTeamOwnershipRequest(INVITEE_PROFILE_ID)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("New team owner must be an active member of this team.");
    }

    @Test
    void ownerCannotTransferOwnershipToOrganizer() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(profileRepository.findById(INVITEE_PROFILE_ID))
                .thenReturn(Optional.of(profile(INVITEE_PROFILE_ID, ProfileRole.ORGANIZER)));

        assertThatThrownBy(() -> teamRosterService.transferOwnership(
                TEAM_ID,
                new TransferTeamOwnershipRequest(INVITEE_PROFILE_ID)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Team owner must be a player.");
    }

    @Test
    void ownerCannotTransferOwnershipToMissingProfile() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));

        assertThatThrownBy(() -> teamRosterService.transferOwnership(
                TEAM_ID,
                new TransferTeamOwnershipRequest(INVITEE_PROFILE_ID)))
                .isInstanceOf(si.um.feri.dotaops.backend.common.error.ResourceNotFoundException.class);
    }

    @Test
    void ownershipTransferRejectsMissingTeam() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.findByIdForUpdate(TEAM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamRosterService.transferOwnership(
                TEAM_ID,
                new TransferTeamOwnershipRequest(INVITEE_PROFILE_ID)))
                .isInstanceOf(si.um.feri.dotaops.backend.common.error.ResourceNotFoundException.class);
    }

    @Test
    void ownerCannotTransferOwnershipToSelf() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));

        assertThatThrownBy(() -> teamRosterService.transferOwnership(
                TEAM_ID,
                new TransferTeamOwnershipRequest(CAPTAIN_PROFILE_ID)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Profile is already the team owner.");
    }

    private static Team team() {
        return team(CAPTAIN_PROFILE_ID);
    }

    private static Team team(UUID captainProfileId) {
        return new Team(
                TEAM_ID,
                "Ancient Stack",
                "AS",
                "ancient-stack",
                captainProfileId,
                "Captain",
                "EU",
                null,
                null,
                AUTH_USER_ID,
                NOW,
                NOW);
    }

    private static TeamMember member(boolean active, TeamMemberRole role) {
        return member(MEMBER_ID, INVITEE_PROFILE_ID, active, role);
    }

    private static TeamMember member(UUID memberId, UUID profileId, boolean active, TeamMemberRole role) {
        return new TeamMember(
                memberId,
                TEAM_ID,
                profileId,
                "CarryOne",
                "Carry One",
                null,
                role,
                active,
                NOW,
                active ? null : NOW,
                NOW);
    }

    private static TeamInvitation invitation(
            TeamInvitationStatus status,
            UUID inviteeProfileId,
            String inviteeEmail,
            OffsetDateTime acceptedAt
    ) {
        return invitation(status, inviteeProfileId, inviteeEmail, acceptedAt, OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
    }

    private static TeamInvitation invitation(
            TeamInvitationStatus status,
            UUID inviteeProfileId,
            String inviteeEmail,
            OffsetDateTime acceptedAt,
            OffsetDateTime expiresAt
    ) {
        return new TeamInvitation(
                INVITATION_ID,
                TEAM_ID,
                "Ancient Stack",
                "ancient-stack",
                CAPTAIN_PROFILE_ID,
                "Captain",
                inviteeProfileId,
                "CarryOne",
                inviteeEmail,
                TeamMemberRole.CARRY,
                status,
                expiresAt,
                acceptedAt,
                NOW,
                NOW);
    }

    private static TeamManualPlayer manualPlayer() {
        return new TeamManualPlayer(
                UUID.fromString("88888888-8888-4888-8888-888888888888"),
                TEAM_ID,
                "Guest Mid",
                "guest",
                "local player",
                NOW,
                NOW);
    }

    private static Profile profile(UUID profileId) {
        return profile(profileId, ProfileRole.PLAYER);
    }

    private static Profile profile(UUID profileId, ProfileRole role) {
        return new Profile(
                profileId,
                AUTH_USER_ID,
                "CarryOne",
                "Carry One",
                null,
                null,
                role,
                null,
                null,
                "SI",
                null,
                null,
                NOW,
                NOW);
    }

    private static AuthenticatedProfile authenticatedProfile(UUID profileId, ProfileRole role) {
        return new AuthenticatedProfile(
                profileId,
                AUTH_USER_ID,
                "Captain",
                role);
    }

    private static SupabasePrincipal principal(UUID profileId, String email) {
        return new SupabasePrincipal(
                AUTH_USER_ID,
                email,
                Optional.of(authenticatedProfile(profileId, ProfileRole.PLAYER)),
                null);
    }
}
