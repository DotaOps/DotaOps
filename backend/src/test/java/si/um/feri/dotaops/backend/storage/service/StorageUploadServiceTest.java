package si.um.feri.dotaops.backend.storage.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedProfile;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.config.properties.SupabaseStorageProperties;
import si.um.feri.dotaops.backend.profile.domain.Profile;
import si.um.feri.dotaops.backend.profile.repository.ProfileRepository;
import si.um.feri.dotaops.backend.storage.web.ConfirmStorageUploadRequest;
import si.um.feri.dotaops.backend.storage.web.CreateStorageUploadUrlRequest;
import si.um.feri.dotaops.backend.team.domain.Team;
import si.um.feri.dotaops.backend.team.repository.TeamRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageUploadServiceTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID OTHER_PROFILE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID TEAM_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final SupabaseImageStorageService imageStorageService = mock(SupabaseImageStorageService.class);
    private final StorageUploadService storageUploadService = new StorageUploadService(
            currentUserProvider,
            profileRepository,
            teamRepository,
            imageStorageService,
            new SupabaseStorageProperties(
                    "https://project.supabase.co",
                    "service-role-key",
                    "dotaops-images",
                    "avatars",
                    "team-assets"));

    @Test
    void createCurrentAvatarUploadUrlGeneratesProfileScopedPath() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(PROFILE_ID, ProfileRole.PLAYER));
        String expectedPath = "profiles/" + PROFILE_ID + "/avatar.png";
        when(imageStorageService.createSignedUploadUrl("avatars", expectedPath))
                .thenReturn(signedUpload("avatars", expectedPath));

        var response = storageUploadService.createCurrentAvatarUploadUrl(new CreateStorageUploadUrlRequest(
                "avatar.png",
                "image/png",
                1024L));

        assertThat(response.bucket()).isEqualTo("avatars");
        assertThat(response.path()).isEqualTo(expectedPath);
        assertThat(response.uploadUrl()).contains("/storage/v1/object/upload/sign/avatars/");
        assertThat(response.uploadUrl()).doesNotContain("service-role-key");
        assertThat(response.uploadMethod()).isEqualTo("PUT");
        assertThat(response.maxFileSizeBytes()).isEqualTo(2L * 1024L * 1024L);
        assertThat(response.contentType()).isEqualTo("image/png");
    }

    @Test
    void createCurrentAvatarUploadUrlRejectsSvg() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(PROFILE_ID, ProfileRole.PLAYER));

        assertThatThrownBy(() -> storageUploadService.createCurrentAvatarUploadUrl(new CreateStorageUploadUrlRequest(
                "avatar.svg",
                "image/svg+xml",
                1024L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Avatar must be a png, jpeg or webp image.");

        verify(imageStorageService, never()).createSignedUploadUrl(any(), any());
    }

    @Test
    void createTeamLogoUploadUrlRequiresTeamCaptainPlayer() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(OTHER_PROFILE_ID, ProfileRole.ORGANIZER));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team(PROFILE_ID)));

        assertThatThrownBy(() -> storageUploadService.createTeamLogoUploadUrl(TEAM_ID, new CreateStorageUploadUrlRequest(
                "logo.png",
                "image/png",
                1024L)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only the team captain can manage team storage assets.");

        verify(imageStorageService, never()).createSignedUploadUrl(any(), any());
    }

    @Test
    void createTeamBannerUploadUrlAllowsCaptainAndUsesBannerLimit() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team(PROFILE_ID)));
        String expectedPath = "teams/" + TEAM_ID + "/banner.webp";
        when(imageStorageService.createSignedUploadUrl("team-assets", expectedPath))
                .thenReturn(signedUpload("team-assets", expectedPath));

        var response = storageUploadService.createTeamBannerUploadUrl(TEAM_ID, new CreateStorageUploadUrlRequest(
                "banner.webp",
                "image/webp",
                4L * 1024L * 1024L));

        assertThat(response.bucket()).isEqualTo("team-assets");
        assertThat(response.path()).isEqualTo(expectedPath);
        assertThat(response.maxFileSizeBytes()).isEqualTo(5L * 1024L * 1024L);
    }

    @Test
    void createTeamLogoUploadUrlRejectsOversizedLogo() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team(PROFILE_ID)));

        assertThatThrownBy(() -> storageUploadService.createTeamLogoUploadUrl(TEAM_ID, new CreateStorageUploadUrlRequest(
                "logo.png",
                "image/png",
                3L * 1024L * 1024L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Team logo image must be 2MB or smaller.");
    }

    @Test
    void confirmCurrentAvatarPersistsBackendGeneratedPublicUrlAndPath() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(PROFILE_ID, ProfileRole.PLAYER));
        String path = "profiles/" + PROFILE_ID + "/avatar.jpg";
        String publicUrl = "https://project.supabase.co/storage/v1/object/public/avatars/" + path;
        when(imageStorageService.publicUrl("avatars", path)).thenReturn(publicUrl);
        when(profileRepository.updateAvatarStorage(PROFILE_ID, publicUrl, path))
                .thenReturn(Optional.of(profile()));

        var response = storageUploadService.confirmCurrentAvatar(new ConfirmStorageUploadRequest(
                "avatars",
                path,
                publicUrl));

        assertThat(response.avatarUrl()).isEqualTo(publicUrl);
        assertThat(response.persisted()).isTrue();
        verify(profileRepository).updateAvatarStorage(PROFILE_ID, publicUrl, path);
    }

    @Test
    void confirmTeamLogoRejectsMismatchedPublicUrl() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile(PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team(PROFILE_ID)));
        String path = "teams/" + TEAM_ID + "/logo.png";
        when(imageStorageService.publicUrl("team-assets", path))
                .thenReturn("https://project.supabase.co/storage/v1/object/public/team-assets/" + path);

        assertThatThrownBy(() -> storageUploadService.confirmTeamLogo(TEAM_ID, new ConfirmStorageUploadRequest(
                "team-assets",
                path,
                "https://attacker.example.test/logo.png")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Storage public URL does not match the bucket and path.");

        verify(teamRepository, never()).updateLogoStorage(eq(TEAM_ID), any(), any());
    }

    private static AuthenticatedProfile authenticatedProfile(UUID profileId, ProfileRole role) {
        return new AuthenticatedProfile(
                profileId,
                AUTH_USER_ID,
                "MidPulse",
                role);
    }

    private static SignedStorageUpload signedUpload(String bucket, String path) {
        return new SignedStorageUpload(
                bucket,
                path,
                "https://project.supabase.co/storage/v1/object/upload/sign/" + bucket + "/" + path + "?token=signed-token",
                "signed-token",
                "https://project.supabase.co/storage/v1/object/public/" + bucket + "/" + path,
                7200);
    }

    private static Team team(UUID captainProfileId) {
        OffsetDateTime now = OffsetDateTime.parse("2026-05-12T00:00:00Z");
        return new Team(
                TEAM_ID,
                "Ancient Stack",
                "AS",
                "ancient-stack",
                captainProfileId,
                "MidPulse",
                "EU",
                null,
                null,
                "Tier two squad",
                AUTH_USER_ID,
                now,
                now);
    }

    private static Profile profile() {
        OffsetDateTime now = OffsetDateTime.parse("2026-05-12T00:00:00Z");
        return new Profile(
                PROFILE_ID,
                AUTH_USER_ID,
                "MidPulse",
                "Mid Pulse",
                null,
                null,
                ProfileRole.PLAYER,
                null,
                null,
                null,
                null,
                now,
                now,
                now,
                now);
    }
}
