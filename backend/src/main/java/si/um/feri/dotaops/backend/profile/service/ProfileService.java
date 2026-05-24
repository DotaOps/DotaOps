package si.um.feri.dotaops.backend.profile.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedProfile;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.auth.service.SupabasePrincipal;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.ConflictException;
import si.um.feri.dotaops.backend.common.error.ResourceNotFoundException;
import si.um.feri.dotaops.backend.common.pagination.PageResponse;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaPlayerProfile;
import si.um.feri.dotaops.backend.opendota.service.DotaAccountIdConverter;
import si.um.feri.dotaops.backend.opendota.service.OpenDotaClient;
import si.um.feri.dotaops.backend.profile.domain.Profile;
import si.um.feri.dotaops.backend.profile.repository.CreateProfileCommand;
import si.um.feri.dotaops.backend.profile.repository.ProfileBootstrapRepository;
import si.um.feri.dotaops.backend.profile.repository.ProfileRepository;
import si.um.feri.dotaops.backend.profile.repository.UpdateProfileCommand;
import si.um.feri.dotaops.backend.profile.web.AvatarUploadResponse;
import si.um.feri.dotaops.backend.profile.web.CreateProfileRequest;
import si.um.feri.dotaops.backend.profile.web.ProfileResponse;
import si.um.feri.dotaops.backend.profile.web.UpdateProfileRequest;
import si.um.feri.dotaops.backend.storage.service.StoredImage;
import si.um.feri.dotaops.backend.storage.service.SupabaseImageStorageService;

@Service
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final ProfileBootstrapRepository profileBootstrapRepository;
    private final SupabaseImageStorageService imageStorageService;
    private final OpenDotaClient openDotaClient;
    private final CurrentUserProvider currentUserProvider;

    public ProfileService(
            ProfileRepository profileRepository,
            ProfileBootstrapRepository profileBootstrapRepository,
            SupabaseImageStorageService imageStorageService,
            OpenDotaClient openDotaClient,
            CurrentUserProvider currentUserProvider
    ) {
        this.profileRepository = profileRepository;
        this.profileBootstrapRepository = profileBootstrapRepository;
        this.imageStorageService = imageStorageService;
        this.openDotaClient = openDotaClient;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProfileResponse> listProfiles(String search, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        long offset = (long) safePage * safeSize;

        List<ProfileResponse> profiles = profileRepository.findProfiles(search, safeSize, offset)
                .stream()
                .map(ProfileResponse::from)
                .toList();
        long total = profileRepository.countProfiles(search);

        return PageResponse.from(new PageImpl<>(
                profiles,
                PageRequest.of(safePage, safeSize),
                total));
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID profileId) {
        return profileRepository.findById(profileId)
                .map(ProfileResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", "id", profileId));
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfileByNickname(String nickname) {
        String normalizedNickname = normalizeRequired(nickname);

        return profileRepository.findByNickname(normalizedNickname)
                .map(ProfileResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", "nickname", normalizedNickname));
    }

    @Transactional
    public ProfileResponse getCurrentProfile() {
        return ProfileResponse.from(ensureCurrentProfile());
    }

    @Transactional
    public ProfileMutationResult createCurrentProfile(CreateProfileRequest request) {
        UUID authUserId = currentUserProvider.requireAuthUserId();
        Optional<Profile> existingProfile = profileRepository.findByAuthUserId(authUserId);
        ProfileRole requestedRole = resolveSelfSelectedRole(
                request.desiredRole(),
                existingProfile.map(Profile::role).orElse(ProfileRole.PLAYER));

        if (existingProfile.isPresent()) {
            Profile updatedProfile = updateExistingProfileFromCreate(existingProfile.orElseThrow(), request, requestedRole);
            return new ProfileMutationResult(ProfileResponse.from(updatedProfile), false);
        }

        try {
            Profile profile = profileRepository.create(new CreateProfileCommand(
                    authUserId,
                    normalizeRequired(request.nickname()),
                    normalizeOptional(request.displayName()),
                    normalizeOptional(request.avatarUrl()),
                    normalizeOptional(request.bio()),
                    normalizeCountryCode(request.countryCode()),
                    requestedRole));

            return new ProfileMutationResult(ProfileResponse.from(profile), true);
        } catch (DataIntegrityViolationException exception) {
            throw profileConstraintException(exception);
        }
    }

    @Transactional
    public ProfileResponse updateCurrentProfile(UpdateProfileRequest request) {
        if (!request.hasChanges()) {
            throw new BadRequestException("At least one profile field must be provided.");
        }

        UUID profileId = ensureCurrentProfile().id();

        try {
            return profileRepository.updateById(
                            profileId,
                            new UpdateProfileCommand(
                                    request.hasNickname(),
                                    request.hasNickname() ? normalizeRequired(request.nickname()) : null,
                                    request.hasDisplayName(),
                                    request.hasDisplayName() ? normalizeOptional(request.displayName()) : null,
                                    request.hasAvatarUrl(),
                                    request.hasAvatarUrl() ? normalizeOptional(request.avatarUrl()) : null,
                                    request.hasBio(),
                                    request.hasBio() ? normalizeOptional(request.bio()) : null,
                                    request.hasCountryCode(),
                                    request.hasCountryCode() ? normalizeCountryCode(request.countryCode()) : null))
                    .map(ProfileResponse::from)
                    .orElseThrow(() -> new ResourceNotFoundException("Profile", "id", profileId));
        } catch (DataIntegrityViolationException exception) {
            throw profileConstraintException(exception);
        }
    }

    public AvatarUploadResponse updateCurrentAvatar(MultipartFile avatar) {
        Profile currentProfile = ensureCurrentProfile();
        StoredImage storedAvatar = imageStorageService.storeProfileAvatar(currentProfile.id(), avatar);

        try {
            profileRepository.updateById(
                            currentProfile.id(),
                            new UpdateProfileCommand(
                                    false,
                                    null,
                                    false,
                                    null,
                                    true,
                                    storedAvatar.publicUrl(),
                                    false,
                                    null,
                                    false,
                                    null))
                    .orElseThrow(() -> new ResourceNotFoundException("Profile", "id", currentProfile.id()));
        } catch (DataIntegrityViolationException exception) {
            throw profileConstraintException(exception);
        }

        return new AvatarUploadResponse(storedAvatar.publicUrl(), "Avatar uploaded successfully.", true);
    }

    public ProfileResponse syncCurrentOpenDotaProfile() {
        Profile currentProfile = ensureCurrentProfile();
        long accountId = currentOpenDotaAccountId(currentProfile);
        Optional<OpenDotaPlayerProfile> playerProfile = openDotaClient.fetchPlayer(accountId);

        if (playerProfile.isEmpty()) {
            profileBootstrapRepository.markOpenDotaProfileSyncFailed(currentProfile.id(), accountId);
            throw new ConflictException("OpenDota profile could not be synced for the current Steam account.");
        }

        profileBootstrapRepository.markOpenDotaProfileSynced(currentProfile.id(), playerProfile.orElseThrow());

        return profileRepository.findById(currentProfile.id())
                .map(ProfileResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", "id", currentProfile.id()));
    }

    private String normalizeRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Required profile field is blank.");
        }

        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String normalizeCountryCode(String value) {
        String normalized = normalizeOptional(value);

        if (normalized == null) {
            return null;
        }

        return normalized.toUpperCase(Locale.ROOT);
    }

    private long currentOpenDotaAccountId(Profile profile) {
        if (profile.opendotaAccountId() != null) {
            return profile.opendotaAccountId();
        }

        if (profile.steamId() == null || profile.steamId().isBlank()) {
            throw new ConflictException("A Steam account must be connected before OpenDota sync.");
        }

        try {
            return DotaAccountIdConverter.steamId64ToAccountId32(profile.steamId());
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("Connected Steam account cannot be converted to an OpenDota account id.");
        }
    }

    private Profile ensureCurrentProfile() {
        Optional<SupabasePrincipal> currentUser = currentUserProvider.currentUser();
        if (currentUser.isPresent()) {
            return ensureProfileForPrincipal(currentUser.orElseThrow());
        }

        AuthenticatedProfile currentProfile = currentUserProvider.requireProfile();
        return profileRepository.findById(currentProfile.profileId())
                .orElseThrow(() -> new ResourceNotFoundException("Profile", "id", currentProfile.profileId()));
    }

    private Profile ensureProfileForPrincipal(SupabasePrincipal principal) {
        if (principal.profile().isPresent()) {
            UUID profileId = principal.profile().orElseThrow().profileId();
            Optional<Profile> profile = profileRepository.findById(profileId);
            if (profile.isPresent()) {
                return profile.orElseThrow();
            }
        }

        UUID authUserId = principal.authUserId();
        if (authUserId == null) {
            AuthenticatedProfile currentProfile = currentUserProvider.requireProfile();
            return profileRepository.findById(currentProfile.profileId())
                    .orElseThrow(() -> new ResourceNotFoundException("Profile", "id", currentProfile.profileId()));
        }

        return profileRepository.findByAuthUserId(authUserId)
                .orElseGet(() -> createDefaultProfile(principal));
    }

    private Profile createDefaultProfile(SupabasePrincipal principal) {
        UUID authUserId = principal.authUserId();
        ProfileRole role = resolveMetadataRole(desiredRoleFromClaims(principal));
        String nickname = availableDefaultNickname(principal.email(), authUserId);

        try {
            return profileRepository.create(new CreateProfileCommand(
                    authUserId,
                    nickname,
                    nickname,
                    null,
                    null,
                    null,
                    role));
        } catch (DataIntegrityViolationException exception) {
            throw profileConstraintException(exception);
        }
    }

    private Profile updateExistingProfileFromCreate(
            Profile existingProfile,
            CreateProfileRequest request,
            ProfileRole requestedRole
    ) {
        try {
            Profile updatedProfile = profileRepository.updateById(
                            existingProfile.id(),
                            new UpdateProfileCommand(
                                    true,
                                    normalizeRequired(request.nickname()),
                                    true,
                                    normalizeOptional(request.displayName()),
                                    true,
                                    normalizeOptional(request.avatarUrl()),
                                    true,
                                    normalizeOptional(request.bio()),
                                    true,
                                    normalizeCountryCode(request.countryCode())))
                    .orElseThrow(() -> new ResourceNotFoundException("Profile", "id", existingProfile.id()));

            if (existingProfile.role() == ProfileRole.ADMIN) {
                return updatedProfile;
            }

            ProfileRole targetRole = organizerCapabilityRole(existingProfile.role(), requestedRole);
            if (targetRole != updatedProfile.role()) {
                return profileRepository.updateRoleById(updatedProfile.id(), targetRole)
                        .orElseThrow(() -> new ResourceNotFoundException("Profile", "id", updatedProfile.id()));
            }

            return updatedProfile;
        } catch (DataIntegrityViolationException exception) {
            throw profileConstraintException(exception);
        }
    }

    private ProfileRole resolveSelfSelectedRole(String value, ProfileRole defaultRole) {
        if (value == null || value.isBlank()) {
            return defaultRole;
        }

        String normalized = value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_');

        return switch (normalized) {
            case "player" -> ProfileRole.PLAYER;
            case "organizer" -> ProfileRole.ORGANIZER;
            case "admin" -> throw new BadRequestException("ADMIN role cannot be self-selected.");
            case "captain", "team_captain" -> throw new BadRequestException(
                    "Team captain is assigned through team ownership, not as a global account role.");
            default -> throw new BadRequestException("Unsupported profile role.");
        };
    }

    private ProfileRole resolveMetadataRole(String value) {
        if (value == null || value.isBlank()) {
            return ProfileRole.PLAYER;
        }

        String normalized = value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_');

        return "organizer".equals(normalized) ? ProfileRole.ORGANIZER : ProfileRole.PLAYER;
    }

    private ProfileRole organizerCapabilityRole(ProfileRole existingRole, ProfileRole requestedRole) {
        if (existingRole == ProfileRole.ORGANIZER || requestedRole == ProfileRole.ORGANIZER) {
            return ProfileRole.ORGANIZER;
        }

        return requestedRole;
    }

    private String desiredRoleFromClaims(SupabasePrincipal principal) {
        if (principal.token() == null || principal.token().claims() == null) {
            return null;
        }

        Object appMetadata = principal.token().claims().get("app_metadata");
        if (appMetadata instanceof java.util.Map<?, ?> metadata) {
            Object desiredRole = metadata.get("desired_role");
            if (desiredRole instanceof String desiredRoleValue) {
                return desiredRoleValue;
            }

            Object accountType = metadata.get("account_type");
            if (accountType instanceof String accountTypeValue) {
                return accountTypeValue;
            }
        }

        Object userMetadata = principal.token().claims().get("user_metadata");
        if (userMetadata instanceof java.util.Map<?, ?> metadata) {
            Object desiredRole = metadata.get("desired_role");
            if (desiredRole instanceof String desiredRoleValue) {
                return desiredRoleValue;
            }

            Object accountType = metadata.get("account_type");
            if (accountType instanceof String accountTypeValue) {
                return accountTypeValue;
            }
        }

        return null;
    }

    private String availableDefaultNickname(String email, UUID authUserId) {
        String suffix = authUserId.toString().replace("-", "").substring(0, 8);
        String base = email == null ? null : email.split("@", 2)[0];
        String normalized = normalizeDefaultNickname(base, suffix);

        if (profileRepository.findByNickname(normalized).isEmpty()) {
            return normalized;
        }

        String candidate = withSuffix(normalized, suffix);
        int attempt = 1;
        while (profileRepository.findByNickname(candidate).isPresent()) {
            candidate = withSuffix(normalized, suffix + "_" + attempt);
            attempt++;
        }

        return candidate;
    }

    private String normalizeDefaultNickname(String value, String suffix) {
        String normalized = value == null ? "" : value.trim();
        normalized = normalized
                .replaceAll("\\s+", "_")
                .replaceAll("[^A-Za-z0-9_-]", "_")
                .replaceAll("(^_+|_+$)", "");

        if (normalized.length() < 2) {
            normalized = "player_" + suffix;
        }

        if (normalized.length() > 40) {
            normalized = normalized.substring(0, 40);
        }

        return normalized;
    }

    private String withSuffix(String base, String suffix) {
        String fullSuffix = "_" + suffix;
        int maxBaseLength = Math.max(2, 40 - fullSuffix.length());
        String trimmedBase = base.length() > maxBaseLength ? base.substring(0, maxBaseLength) : base;

        return trimmedBase + fullSuffix;
    }

    private BadRequestException profileConstraintException(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();

        if (message != null && message.contains("profiles_auth_user_id_key")) {
            return new BadRequestException("Authenticated user already has a profile.");
        }

        if (message != null && (message.contains("profiles_nickname_ci_unique_idx")
                || message.contains("profiles_nickname_key"))) {
            return new BadRequestException("Profile nickname is already in use.");
        }

        return new BadRequestException("Profile data violates a database constraint.");
    }
}
