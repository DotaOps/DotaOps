package si.um.feri.dotaops.backend.storage.web;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import si.um.feri.dotaops.backend.BackendApplication;
import si.um.feri.dotaops.backend.auth.domain.AuthenticatedProfile;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.repository.AuthenticatedProfileRepository;
import si.um.feri.dotaops.backend.auth.service.SupabaseJwtTestSupport;
import si.um.feri.dotaops.backend.profile.web.AvatarUploadResponse;
import si.um.feri.dotaops.backend.storage.service.StorageUploadService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
        BackendApplication.class,
        StorageUploadControllerTest.StorageUploadControllerTestConfig.class
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "dotaops.supabase.auth.jwt-secret=" + SupabaseJwtTestSupport.SECRET,
        "dotaops.supabase.auth.issuer=" + SupabaseJwtTestSupport.ISSUER,
        "dotaops.supabase.auth.audience=" + SupabaseJwtTestSupport.AUDIENCE,
        "dotaops.steam.session.jwt-secret=" + SupabaseJwtTestSupport.SECRET,
        "dotaops.steam.session.ttl=1h"
})
class StorageUploadControllerTest {

    private static final UUID PLAYER_AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ORGANIZER_AUTH_USER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PLAYER_PROFILE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID TEAM_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StorageUploadService storageUploadService;

    @Autowired
    private AuthenticatedProfileRepository authenticatedProfileRepository;

    @BeforeEach
    void setUp() {
        Mockito.reset(storageUploadService, authenticatedProfileRepository);
        when(authenticatedProfileRepository.findByAuthUserId(PLAYER_AUTH_USER_ID))
                .thenReturn(Optional.of(authenticatedProfile(PLAYER_PROFILE_ID, PLAYER_AUTH_USER_ID, ProfileRole.PLAYER)));
        when(authenticatedProfileRepository.findByAuthUserId(ORGANIZER_AUTH_USER_ID))
                .thenReturn(Optional.of(authenticatedProfile(
                        UUID.fromString("55555555-5555-4555-8555-555555555555"),
                        ORGANIZER_AUTH_USER_ID,
                        ProfileRole.ORGANIZER)));
    }

    @Test
    void createCurrentAvatarUploadUrlRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/me/avatar/upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUploadRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void createCurrentAvatarUploadUrlReturnsSignedUploadContract() throws Exception {
        String path = "profiles/" + PLAYER_PROFILE_ID + "/avatar.png";
        when(storageUploadService.createCurrentAvatarUploadUrl(any()))
                .thenReturn(uploadResponse("avatars", path, 2L * 1024L * 1024L));

        mockMvc.perform(post("/api/me/avatar/upload-url")
                        .header("Authorization", bearerToken(PLAYER_AUTH_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUploadRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bucket").value("avatars"))
                .andExpect(jsonPath("$.data.path").value(path))
                .andExpect(jsonPath("$.data.uploadMethod").value("PUT"))
                .andExpect(jsonPath("$.data.uploadUrl").value(
                        "https://project.supabase.co/storage/v1/object/upload/sign/avatars/" + path + "?token=signed-token"))
                .andExpect(jsonPath("$.data.uploadUrl").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("service-role-key"))))
                .andExpect(jsonPath("$.data.publicUrl").value(
                        "https://project.supabase.co/storage/v1/object/public/avatars/" + path))
                .andExpect(jsonPath("$.data.maxFileSizeBytes").value(2L * 1024L * 1024L))
                .andExpect(jsonPath("$.data.upsert").value(true));
    }

    @Test
    void createTeamLogoUploadUrlRejectsOrganizerAtSecurityLayer() throws Exception {
        mockMvc.perform(post("/api/teams/" + TEAM_ID + "/logo/upload-url")
                        .header("Authorization", bearerToken(ORGANIZER_AUTH_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUploadRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(storageUploadService, never()).createTeamLogoUploadUrl(eq(TEAM_ID), any());
    }

    @Test
    void createTeamLogoUploadUrlAllowsPlayerRoute() throws Exception {
        String path = "teams/" + TEAM_ID + "/logo.png";
        when(storageUploadService.createTeamLogoUploadUrl(eq(TEAM_ID), any()))
                .thenReturn(uploadResponse("team-assets", path, 2L * 1024L * 1024L));

        mockMvc.perform(post("/api/teams/" + TEAM_ID + "/logo/upload-url")
                        .header("Authorization", bearerToken(PLAYER_AUTH_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUploadRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bucket").value("team-assets"))
                .andExpect(jsonPath("$.data.path").value(path));
    }

    @Test
    void invalidUploadMetadataReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/me/avatar/upload-url")
                        .header("Authorization", bearerToken(PLAYER_AUTH_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "",
                                  "contentType": "image/png",
                                  "fileSizeBytes": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void confirmCurrentAvatarReturnsPersistedAvatarUrl() throws Exception {
        String path = "profiles/" + PLAYER_PROFILE_ID + "/avatar.png";
        String publicUrl = "https://project.supabase.co/storage/v1/object/public/avatars/" + path;
        when(storageUploadService.confirmCurrentAvatar(any()))
                .thenReturn(new AvatarUploadResponse(publicUrl, "Avatar upload confirmed.", true));

        mockMvc.perform(post("/api/me/avatar/confirm")
                        .header("Authorization", bearerToken(PLAYER_AUTH_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucket": "avatars",
                                  "path": "%s",
                                  "publicUrl": "%s"
                                }
                                """.formatted(path, publicUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value(publicUrl))
                .andExpect(jsonPath("$.data.persisted").value(true));
    }

    private static String bearerToken(UUID authUserId) throws Exception {
        return "Bearer " + SupabaseJwtTestSupport.token(authUserId, Instant.now());
    }

    private static String validUploadRequest() {
        return """
                {
                  "fileName": "image.png",
                  "contentType": "image/png",
                  "fileSizeBytes": 1024
                }
                """;
    }

    private static StorageUploadUrlResponse uploadResponse(String bucket, String path, long maxFileSizeBytes) {
        return new StorageUploadUrlResponse(
                bucket,
                path,
                "https://project.supabase.co/storage/v1/object/upload/sign/" + bucket + "/" + path + "?token=signed-token",
                "signed-token",
                "PUT",
                Map.of("content-type", "image/png", "x-upsert", "true"),
                "https://project.supabase.co/storage/v1/object/public/" + bucket + "/" + path,
                7200,
                maxFileSizeBytes,
                "image/png",
                true);
    }

    private static AuthenticatedProfile authenticatedProfile(UUID profileId, UUID authUserId, ProfileRole role) {
        return new AuthenticatedProfile(
                profileId,
                authUserId,
                "MidPulse",
                role);
    }

    @TestConfiguration
    static class StorageUploadControllerTestConfig {

        @Bean
        @Primary
        StorageUploadService storageUploadService() {
            return Mockito.mock(StorageUploadService.class);
        }

        @Bean
        @Primary
        AuthenticatedProfileRepository authenticatedProfileRepository() {
            return Mockito.mock(AuthenticatedProfileRepository.class);
        }
    }
}
