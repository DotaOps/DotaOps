package si.um.feri.dotaops.backend.team.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import si.um.feri.dotaops.backend.common.api.ApiResponse;
import si.um.feri.dotaops.backend.team.service.TeamRosterService;

@RestController
@RequestMapping("/api")
public class TeamRosterController {

    private final TeamRosterService teamRosterService;

    public TeamRosterController(TeamRosterService teamRosterService) {
        this.teamRosterService = teamRosterService;
    }

    @GetMapping("/teams/{teamId}/members")
    ApiResponse<List<TeamMemberResponse>> listActiveMembers(@PathVariable UUID teamId) {
        return ApiResponse.of(teamRosterService.listActiveMembers(teamId));
    }

    @GetMapping("/teams/by-slug/{slug}/members")
    ApiResponse<List<TeamMemberResponse>> listActiveMembersByTeamSlug(@PathVariable String slug) {
        return ApiResponse.of(teamRosterService.listActiveMembersByTeamSlug(slug));
    }

    @GetMapping("/teams/{teamId}/manual-players")
    ApiResponse<List<TeamManualPlayerResponse>> listManualPlayers(@PathVariable UUID teamId) {
        return ApiResponse.of(teamRosterService.listManualPlayers(teamId));
    }

    @GetMapping("/teams/by-slug/{slug}/manual-players")
    ApiResponse<List<TeamManualPlayerResponse>> listManualPlayersByTeamSlug(@PathVariable String slug) {
        return ApiResponse.of(teamRosterService.listManualPlayersByTeamSlug(slug));
    }

    @GetMapping("/me/team")
    ApiResponse<CurrentTeamResponse> getCurrentTeam() {
        return ApiResponse.of(teamRosterService.getCurrentTeam());
    }

    @PostMapping("/teams/{teamId}/transfer-ownership")
    ApiResponse<CurrentTeamResponse> transferOwnership(
            @PathVariable UUID teamId,
            @Valid @RequestBody TransferTeamOwnershipRequest request
    ) {
        return ApiResponse.of(teamRosterService.transferOwnership(teamId, request));
    }

    @PostMapping("/teams/{teamId}/members")
    ResponseEntity<ApiResponse<TeamMemberResponse>> addMember(
            @PathVariable UUID teamId,
            @Valid @RequestBody AddTeamMemberRequest request
    ) {
        TeamMemberResponse response = teamRosterService.addMember(teamId, request);

        return ResponseEntity
                .created(URI.create("/api/teams/" + teamId + "/members/" + response.id()))
                .body(ApiResponse.of(response));
    }

    @PatchMapping("/teams/{teamId}/members/{memberId}")
    ApiResponse<TeamMemberResponse> updateMemberRole(
            @PathVariable UUID teamId,
            @PathVariable UUID memberId,
            @Valid @RequestBody UpdateTeamMemberRequest request
    ) {
        return ApiResponse.of(teamRosterService.updateMemberRole(teamId, memberId, request));
    }

    @DeleteMapping("/teams/{teamId}/members/{memberId}")
    ApiResponse<TeamMemberResponse> deactivateMember(
            @PathVariable UUID teamId,
            @PathVariable UUID memberId
    ) {
        return ApiResponse.of(teamRosterService.deactivateMember(teamId, memberId));
    }

    @PostMapping("/teams/{teamId}/manual-players")
    ResponseEntity<ApiResponse<TeamManualPlayerResponse>> createManualPlayer(
            @PathVariable UUID teamId,
            @Valid @RequestBody CreateTeamManualPlayerRequest request
    ) {
        TeamManualPlayerResponse response = teamRosterService.createManualPlayer(teamId, request);

        return ResponseEntity
                .created(URI.create("/api/teams/" + teamId + "/manual-players/" + response.id()))
                .body(ApiResponse.of(response));
    }

    @PatchMapping("/teams/{teamId}/manual-players/{manualPlayerId}")
    ApiResponse<TeamManualPlayerResponse> updateManualPlayer(
            @PathVariable UUID teamId,
            @PathVariable UUID manualPlayerId,
            @Valid @RequestBody UpdateTeamManualPlayerRequest request
    ) {
        return ApiResponse.of(teamRosterService.updateManualPlayer(teamId, manualPlayerId, request));
    }

    @DeleteMapping("/teams/{teamId}/manual-players/{manualPlayerId}")
    ApiResponse<TeamManualPlayerResponse> deleteManualPlayer(
            @PathVariable UUID teamId,
            @PathVariable UUID manualPlayerId
    ) {
        return ApiResponse.of(teamRosterService.deleteManualPlayer(teamId, manualPlayerId));
    }

    @GetMapping("/teams/{teamId}/invitations")
    ApiResponse<List<TeamInvitationResponse>> listTeamInvitations(
            @PathVariable UUID teamId,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.of(teamRosterService.listTeamInvitations(teamId, status));
    }

    @PostMapping("/teams/{teamId}/invitations")
    ResponseEntity<ApiResponse<TeamInvitationResponse>> createInvitation(
            @PathVariable UUID teamId,
            @Valid @RequestBody CreateTeamInvitationRequest request
    ) {
        TeamInvitationResponse response = teamRosterService.createInvitation(teamId, request);

        return ResponseEntity
                .created(URI.create("/api/team-invitations/" + response.id()))
                .body(ApiResponse.of(response));
    }

    @GetMapping("/me/team-invitations")
    ApiResponse<List<TeamInvitationResponse>> listCurrentUserInvitations(
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.of(teamRosterService.listCurrentUserInvitations(status));
    }

    @PostMapping("/team-invitations/{invitationId}/accept")
    ApiResponse<TeamInvitationResponse> acceptInvitation(@PathVariable UUID invitationId) {
        return ApiResponse.of(teamRosterService.acceptInvitation(invitationId));
    }

    @PostMapping("/team-invitations/{invitationId}/decline")
    ApiResponse<TeamInvitationResponse> declineInvitation(@PathVariable UUID invitationId) {
        return ApiResponse.of(teamRosterService.declineInvitation(invitationId));
    }

    @PostMapping("/team-invitations/{invitationId}/cancel")
    ApiResponse<TeamInvitationResponse> cancelInvitation(@PathVariable UUID invitationId) {
        return ApiResponse.of(teamRosterService.cancelInvitation(invitationId));
    }
}
