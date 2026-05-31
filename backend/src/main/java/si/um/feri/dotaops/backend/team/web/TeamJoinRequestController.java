package si.um.feri.dotaops.backend.team.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import si.um.feri.dotaops.backend.common.api.ApiResponse;
import si.um.feri.dotaops.backend.team.service.TeamJoinRequestService;

@RestController
@RequestMapping("/api")
public class TeamJoinRequestController {

    private final TeamJoinRequestService teamJoinRequestService;

    public TeamJoinRequestController(TeamJoinRequestService teamJoinRequestService) {
        this.teamJoinRequestService = teamJoinRequestService;
    }

    @PostMapping("/teams/{teamId}/join-requests")
    ResponseEntity<ApiResponse<TeamJoinRequestResponse>> createJoinRequest(
            @PathVariable UUID teamId,
            @Valid @RequestBody CreateTeamJoinRequestRequest request
    ) {
        TeamJoinRequestResponse response = teamJoinRequestService.createJoinRequest(teamId, request);

        return ResponseEntity
                .created(URI.create("/api/team-join-requests/" + response.id()))
                .body(ApiResponse.of(response));
    }

    @GetMapping("/me/team-join-requests")
    ApiResponse<List<TeamJoinRequestResponse>> listCurrentUserJoinRequests(
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.of(teamJoinRequestService.listCurrentUserJoinRequests(status));
    }

    @GetMapping("/teams/{teamId}/join-requests")
    ApiResponse<List<TeamJoinRequestResponse>> listTeamJoinRequests(
            @PathVariable UUID teamId,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.of(teamJoinRequestService.listTeamJoinRequests(teamId, status));
    }

    @PostMapping("/team-join-requests/{requestId}/accept")
    ApiResponse<TeamJoinRequestResponse> acceptJoinRequest(@PathVariable UUID requestId) {
        return ApiResponse.of(teamJoinRequestService.acceptJoinRequest(requestId));
    }

    @PostMapping("/team-join-requests/{requestId}/decline")
    ApiResponse<TeamJoinRequestResponse> declineJoinRequest(@PathVariable UUID requestId) {
        return ApiResponse.of(teamJoinRequestService.declineJoinRequest(requestId));
    }

    @PostMapping("/team-join-requests/{requestId}/cancel")
    ApiResponse<TeamJoinRequestResponse> cancelJoinRequest(@PathVariable UUID requestId) {
        return ApiResponse.of(teamJoinRequestService.cancelJoinRequest(requestId));
    }
}
