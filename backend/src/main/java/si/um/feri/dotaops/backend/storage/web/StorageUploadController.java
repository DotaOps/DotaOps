package si.um.feri.dotaops.backend.storage.web;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import si.um.feri.dotaops.backend.common.api.ApiResponse;
import si.um.feri.dotaops.backend.profile.web.AvatarUploadResponse;
import si.um.feri.dotaops.backend.storage.service.StorageUploadService;
import si.um.feri.dotaops.backend.team.web.TeamResponse;

@Validated
@RestController
@RequestMapping("/api")
public class StorageUploadController {

    private final StorageUploadService storageUploadService;

    public StorageUploadController(StorageUploadService storageUploadService) {
        this.storageUploadService = storageUploadService;
    }

    @PostMapping("/me/avatar/upload-url")
    ApiResponse<StorageUploadUrlResponse> createCurrentAvatarUploadUrl(
            @Valid @RequestBody CreateStorageUploadUrlRequest request
    ) {
        return ApiResponse.of(storageUploadService.createCurrentAvatarUploadUrl(request));
    }

    @PostMapping("/me/avatar/confirm")
    ApiResponse<AvatarUploadResponse> confirmCurrentAvatar(
            @Valid @RequestBody ConfirmStorageUploadRequest request
    ) {
        return ApiResponse.of(storageUploadService.confirmCurrentAvatar(request));
    }

    @PostMapping("/teams/{teamId}/logo/upload-url")
    ApiResponse<StorageUploadUrlResponse> createTeamLogoUploadUrl(
            @PathVariable UUID teamId,
            @Valid @RequestBody CreateStorageUploadUrlRequest request
    ) {
        return ApiResponse.of(storageUploadService.createTeamLogoUploadUrl(teamId, request));
    }

    @PostMapping("/teams/{teamId}/logo/confirm")
    ApiResponse<TeamResponse> confirmTeamLogo(
            @PathVariable UUID teamId,
            @Valid @RequestBody ConfirmStorageUploadRequest request
    ) {
        return ApiResponse.of(storageUploadService.confirmTeamLogo(teamId, request));
    }

    @PostMapping("/teams/{teamId}/banner/upload-url")
    ApiResponse<StorageUploadUrlResponse> createTeamBannerUploadUrl(
            @PathVariable UUID teamId,
            @Valid @RequestBody CreateStorageUploadUrlRequest request
    ) {
        return ApiResponse.of(storageUploadService.createTeamBannerUploadUrl(teamId, request));
    }

    @PostMapping("/teams/{teamId}/banner/confirm")
    ApiResponse<TeamResponse> confirmTeamBanner(
            @PathVariable UUID teamId,
            @Valid @RequestBody ConfirmStorageUploadRequest request
    ) {
        return ApiResponse.of(storageUploadService.confirmTeamBanner(teamId, request));
    }
}
