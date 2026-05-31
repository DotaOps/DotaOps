package si.um.feri.dotaops.backend.team.web;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import si.um.feri.dotaops.backend.common.api.ApiResponse;
import si.um.feri.dotaops.backend.common.pagination.PageResponse;
import si.um.feri.dotaops.backend.team.service.TeamService;

@Validated
@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    ApiResponse<PageResponse<TeamResponse>> listTeams(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.of(teamService.listTeams(search, page, size));
    }

    @GetMapping("/{teamId}")
    ApiResponse<TeamResponse> getTeam(@PathVariable UUID teamId) {
        return ApiResponse.of(teamService.getTeam(teamId));
    }

    @GetMapping("/by-slug/{slug}")
    ApiResponse<TeamResponse> getTeamBySlug(@PathVariable String slug) {
        return ApiResponse.of(teamService.getTeamBySlug(slug));
    }

    @PostMapping
    ResponseEntity<ApiResponse<TeamResponse>> createTeam(
            @Valid @RequestBody CreateTeamRequest request
    ) {
        TeamResponse response = teamService.createTeam(request);

        return ResponseEntity
                .created(URI.create("/api/teams/" + response.id()))
                .body(ApiResponse.of(response));
    }

    @PatchMapping("/{teamId}")
    ApiResponse<TeamResponse> updateTeam(
            @PathVariable UUID teamId,
            @Valid @RequestBody UpdateTeamRequest request
    ) {
        return ApiResponse.of(teamService.updateTeam(teamId, request));
    }

    @PostMapping(value = "/{teamId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<TeamResponse> uploadTeamLogo(
            @PathVariable UUID teamId,
            @RequestParam("logo") MultipartFile logo
    ) {
        return ApiResponse.of(teamService.uploadTeamLogo(teamId, logo));
    }

    @PostMapping(value = "/{teamId}/banner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<TeamResponse> uploadTeamBanner(
            @PathVariable UUID teamId,
            @RequestParam("banner") MultipartFile banner
    ) {
        return ApiResponse.of(teamService.uploadTeamBanner(teamId, banner));
    }

    @DeleteMapping("/{teamId}/logo")
    ApiResponse<TeamResponse> deleteTeamLogo(@PathVariable UUID teamId) {
        return ApiResponse.of(teamService.deleteTeamLogo(teamId));
    }

    @DeleteMapping("/{teamId}/banner")
    ApiResponse<TeamResponse> deleteTeamBanner(@PathVariable UUID teamId) {
        return ApiResponse.of(teamService.deleteTeamBanner(teamId));
    }
}
