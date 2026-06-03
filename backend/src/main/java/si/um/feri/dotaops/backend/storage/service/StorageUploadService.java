package si.um.feri.dotaops.backend.storage.service;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedProfile;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.ResourceNotFoundException;
import si.um.feri.dotaops.backend.config.properties.SupabaseStorageProperties;
import si.um.feri.dotaops.backend.profile.repository.ProfileRepository;
import si.um.feri.dotaops.backend.profile.web.AvatarUploadResponse;
import si.um.feri.dotaops.backend.storage.web.ConfirmStorageUploadRequest;
import si.um.feri.dotaops.backend.storage.web.CreateStorageUploadUrlRequest;
import si.um.feri.dotaops.backend.storage.web.StorageUploadUrlResponse;
import si.um.feri.dotaops.backend.team.domain.Team;
import si.um.feri.dotaops.backend.team.repository.TeamRepository;
import si.um.feri.dotaops.backend.team.web.TeamResponse;

@Service
public class StorageUploadService {

    private static final long AVATAR_MAX_BYTES = 2L * 1024L * 1024L;
    private static final long TEAM_LOGO_MAX_BYTES = 2L * 1024L * 1024L;
    private static final long TEAM_BANNER_MAX_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp");
    private static final Pattern SAFE_PATH_PATTERN = Pattern.compile("^[A-Za-z0-9._/-]+$");

    private final CurrentUserProvider currentUserProvider;
    private final ProfileRepository profileRepository;
    private final TeamRepository teamRepository;
    private final SupabaseImageStorageService imageStorageService;
    private final SupabaseStorageProperties storageProperties;

    public StorageUploadService(
            CurrentUserProvider currentUserProvider,
            ProfileRepository profileRepository,
            TeamRepository teamRepository,
            SupabaseImageStorageService imageStorageService,
            SupabaseStorageProperties storageProperties
    ) {
        this.currentUserProvider = currentUserProvider;
        this.profileRepository = profileRepository;
        this.teamRepository = teamRepository;
        this.imageStorageService = imageStorageService;
        this.storageProperties = storageProperties;
    }

    public StorageUploadUrlResponse createCurrentAvatarUploadUrl(CreateStorageUploadUrlRequest request) {
        AuthenticatedProfile profile = currentUserProvider.requireProfile();
        ValidatedImage image = validateImage("Avatar", request, AVATAR_MAX_BYTES);
        String path = "profiles/%s/avatar%s".formatted(profile.profileId(), image.pathExtension());
        SignedStorageUpload upload = imageStorageService.createSignedUploadUrl(storageProperties.avatarsBucket(), path);

        return StorageUploadUrlResponse.from(upload, AVATAR_MAX_BYTES, image.contentType());
    }

    @Transactional
    public AvatarUploadResponse confirmCurrentAvatar(ConfirmStorageUploadRequest request) {
        AuthenticatedProfile profile = currentUserProvider.requireProfile();
        String path = normalizePath(request.path());
        requireBucket(storageProperties.avatarsBucket(), request.bucket());
        if (!path.matches("^profiles/" + Pattern.quote(profile.profileId().toString())
                + "/avatar\\.(png|jpg|jpeg|webp)$")) {
            throw new AccessDeniedException("Avatar upload path does not belong to the current profile.");
        }

        String publicUrl = publicUrl(storageProperties.avatarsBucket(), path, request.publicUrl());
        profileRepository.updateAvatarStorage(profile.profileId(), publicUrl, path)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", "id", profile.profileId()));

        return new AvatarUploadResponse(publicUrl, "Avatar upload confirmed.", true);
    }

    public StorageUploadUrlResponse createTeamLogoUploadUrl(UUID teamId, CreateStorageUploadUrlRequest request) {
        AuthenticatedProfile profile = currentUserProvider.requireProfile();
        Team team = requireTeam(teamId);
        requireTeamCaptain(profile, team);
        ValidatedImage image = validateImage("Team logo", request, TEAM_LOGO_MAX_BYTES);
        String path = "teams/%s/logo%s".formatted(team.id(), image.pathExtension());
        SignedStorageUpload upload = imageStorageService.createSignedUploadUrl(storageProperties.teamAssetsBucket(), path);

        return StorageUploadUrlResponse.from(upload, TEAM_LOGO_MAX_BYTES, image.contentType());
    }

    @Transactional
    public TeamResponse confirmTeamLogo(UUID teamId, ConfirmStorageUploadRequest request) {
        AuthenticatedProfile profile = currentUserProvider.requireProfile();
        Team team = requireTeam(teamId);
        requireTeamCaptain(profile, team);
        String path = normalizePath(request.path());
        requireBucket(storageProperties.teamAssetsBucket(), request.bucket());
        if (!path.matches("^teams/" + Pattern.quote(team.id().toString()) + "/logo\\.(png|jpg|jpeg|webp)$")) {
            throw new AccessDeniedException("Team logo upload path does not belong to this team.");
        }

        String publicUrl = publicUrl(storageProperties.teamAssetsBucket(), path, request.publicUrl());
        return teamRepository.updateLogoStorage(team.id(), publicUrl, path)
                .map(TeamResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", team.id()));
    }

    public StorageUploadUrlResponse createTeamBannerUploadUrl(UUID teamId, CreateStorageUploadUrlRequest request) {
        AuthenticatedProfile profile = currentUserProvider.requireProfile();
        Team team = requireTeam(teamId);
        requireTeamCaptain(profile, team);
        ValidatedImage image = validateImage("Team banner", request, TEAM_BANNER_MAX_BYTES);
        String path = "teams/%s/banner%s".formatted(team.id(), image.pathExtension());
        SignedStorageUpload upload = imageStorageService.createSignedUploadUrl(storageProperties.teamAssetsBucket(), path);

        return StorageUploadUrlResponse.from(upload, TEAM_BANNER_MAX_BYTES, image.contentType());
    }

    @Transactional
    public TeamResponse confirmTeamBanner(UUID teamId, ConfirmStorageUploadRequest request) {
        AuthenticatedProfile profile = currentUserProvider.requireProfile();
        Team team = requireTeam(teamId);
        requireTeamCaptain(profile, team);
        String path = normalizePath(request.path());
        requireBucket(storageProperties.teamAssetsBucket(), request.bucket());
        if (!path.matches("^teams/" + Pattern.quote(team.id().toString()) + "/banner\\.(png|jpg|jpeg|webp)$")) {
            throw new AccessDeniedException("Team banner upload path does not belong to this team.");
        }

        String publicUrl = publicUrl(storageProperties.teamAssetsBucket(), path, request.publicUrl());
        return teamRepository.updateBannerStorage(team.id(), publicUrl, path)
                .map(TeamResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", team.id()));
    }

    private Team requireTeam(UUID teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", teamId));
    }

    private void requireTeamCaptain(AuthenticatedProfile profile, Team team) {
        if (profile.role() != ProfileRole.PLAYER || !profile.profileId().equals(team.captainProfileId())) {
            throw new AccessDeniedException("Only the team captain can manage team storage assets.");
        }
    }

    private ValidatedImage validateImage(String label, CreateStorageUploadUrlRequest request, long maxBytes) {
        if (request == null) {
            throw new BadRequestException(label + " upload metadata is required.");
        }
        if (request.fileSizeBytes() == null || request.fileSizeBytes() <= 0) {
            throw new BadRequestException(label + " file size is required.");
        }
        if (request.fileSizeBytes() > maxBytes) {
            throw new BadRequestException(label + " image must be " + (maxBytes / 1024 / 1024) + "MB or smaller.");
        }

        String contentType = normalizeContentType(label, request.contentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BadRequestException(label + " must be a png, jpeg or webp image.");
        }

        String requestedExtension = extensionFromFileName(label, request.fileName());
        if (!extensionMatchesContentType(requestedExtension, contentType)) {
            throw new BadRequestException(label + " file extension must match the content type.");
        }

        return new ValidatedImage(contentType, extensionForContentType(contentType));
    }

    private String normalizeContentType(String label, String contentType) {
        if (!StringUtils.hasText(contentType)) {
            throw new BadRequestException(label + " content type is required.");
        }

        return contentType.trim().toLowerCase(Locale.ROOT);
    }

    private String extensionFromFileName(String label, String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new BadRequestException(label + " file name is required.");
        }

        String normalized = fileName.trim().toLowerCase(Locale.ROOT);
        int dot = normalized.lastIndexOf('.');
        if (dot < 0 || dot == normalized.length() - 1) {
            throw new BadRequestException(label + " file extension is required.");
        }

        return normalized.substring(dot + 1);
    }

    private boolean extensionMatchesContentType(String extension, String contentType) {
        return switch (contentType) {
            case "image/png" -> "png".equals(extension);
            case "image/jpeg" -> "jpg".equals(extension) || "jpeg".equals(extension);
            case "image/webp" -> "webp".equals(extension);
            default -> false;
        };
    }

    private String extensionForContentType(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            default -> throw new BadRequestException("Image content type is not supported.");
        };
    }

    private void requireBucket(String expectedBucket, String actualBucket) {
        if (!expectedBucket.equals(actualBucket)) {
            throw new BadRequestException("Storage bucket is invalid.");
        }
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            throw new BadRequestException("Storage path is required.");
        }

        String normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!SAFE_PATH_PATTERN.matcher(normalized).matches()
                || normalized.contains("//")
                || normalized.contains("/../")
                || normalized.startsWith("../")
                || normalized.endsWith("/..")
                || normalized.contains("/./")
                || normalized.startsWith("./")
                || normalized.endsWith("/.")) {
            throw new BadRequestException("Storage path is invalid.");
        }

        return normalized;
    }

    private String publicUrl(String bucket, String path, String requestedPublicUrl) {
        String publicUrl = imageStorageService.publicUrl(bucket, path);
        if (StringUtils.hasText(requestedPublicUrl) && !publicUrl.equals(requestedPublicUrl.trim())) {
            throw new BadRequestException("Storage public URL does not match the bucket and path.");
        }

        return publicUrl;
    }

    private record ValidatedImage(
            String contentType,
            String pathExtension
    ) {
    }
}
