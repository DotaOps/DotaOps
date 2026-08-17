package si.um.feri.dotaops.backend.config;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfigurationSource;

import si.um.feri.dotaops.backend.BackendApplication;
import si.um.feri.dotaops.backend.auth.domain.AuthenticatedProfile;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.repository.AuthenticatedProfileRepository;
import si.um.feri.dotaops.backend.auth.service.SupabaseJwtTestSupport;
import si.um.feri.dotaops.backend.auth.service.SupabasePrincipal;
import si.um.feri.dotaops.backend.auth.steam.domain.SteamAuthResult;
import si.um.feri.dotaops.backend.auth.steam.service.SteamSessionTokenService;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
        BackendApplication.class,
        SecurityConfigTest.SecurityTestController.class,
        SecurityConfigTest.SecurityTestRepositoryConfig.class
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "dotaops.supabase.auth.jwt-secret=" + SupabaseJwtTestSupport.SECRET,
        "dotaops.supabase.auth.issuer=" + SupabaseJwtTestSupport.ISSUER,
        "dotaops.supabase.auth.audience=" + SupabaseJwtTestSupport.AUDIENCE,
        "dotaops.steam.session.jwt-secret=" + SupabaseJwtTestSupport.SECRET,
        "dotaops.steam.session.ttl=1h",
        "dotaops.cors.allowed-origin-patterns=https://app.example.test,http://localhost:5173"
})
class SecurityConfigTest {

    private static final UUID PLAYER_AUTH_USER_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID ORGANIZER_AUTH_USER_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID ADMIN_AUTH_USER_ID = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID STEAM_PROFILE_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final String STEAM_ID = "76561198000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthenticatedProfileRepository profileRepository;

    @Autowired
    private SteamSessionTokenService steamSessionTokenService;

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @BeforeEach
    void setUp() {
        Mockito.reset(profileRepository);
        when(profileRepository.findByAuthUserId(any())).thenReturn(Optional.empty());
        when(profileRepository.findByAuthUserId(PLAYER_AUTH_USER_ID)).thenReturn(Optional.of(profile(
                PLAYER_AUTH_USER_ID,
                ProfileRole.PLAYER)));
        when(profileRepository.findByAuthUserId(ORGANIZER_AUTH_USER_ID)).thenReturn(Optional.of(profile(
                ORGANIZER_AUTH_USER_ID,
                ProfileRole.ORGANIZER)));
        when(profileRepository.findByAuthUserId(ADMIN_AUTH_USER_ID)).thenReturn(Optional.of(profile(
                ADMIN_AUTH_USER_ID,
                ProfileRole.ADMIN)));
        when(profileRepository.findByProfileId(STEAM_PROFILE_ID)).thenReturn(Optional.of(new AuthenticatedProfile(
                STEAM_PROFILE_ID,
                null,
                "steam_player",
                ProfileRole.PLAYER)));
    }

    @Test
    void publicEndpointWorksWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void publicTournamentEndpointWorksWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/public/tournaments/security-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("public"));
    }

    @Test
    void publicAnalyticsEndpointWorksWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/public/analytics/security-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("public analytics"));
    }

    @Test
    void protectedEndpointRequiresJwt() throws Exception {
        mockMvc.perform(get("/api/me/security-test"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void dashboardRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/me/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void playerAnalyticsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/me/analytics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void playerProgressAnalyticsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/me/analytics/progress"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void playerHeroAnalyticsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/me/analytics/heroes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void playerHeroMasteryAnalyticsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/me/analytics/heroes/11111111-1111-4111-8111-111111111111/mastery"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void playerInsightsAnalyticsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/me/analytics/insights"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void comparisonAnalyticsRequiresAuthenticationBeforePublicAnalyticsMatcher() throws Exception {
        mockMvc.perform(get("/api/analytics/compare/teams")
                        .queryParam("teamAId", "11111111-1111-4111-8111-111111111111")
                        .queryParam("teamBId", "22222222-2222-4222-8222-222222222222"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void teamPlayerLookupRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/teams/11111111-1111-4111-8111-111111111111/lookups/players"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void invalidJwtReturnsUnauthorizedContract() throws Exception {
        mockMvc.perform(get("/api/me/security-test")
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/me/security-test"));
    }

    @Test
    void invalidJwtIsRejectedOnPublicEndpoint() throws Exception {
        mockMvc.perform(get("/api/health")
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void validJwtCreatesCurrentUserContext() throws Exception {
        mockMvc.perform(get("/api/me/security-test")
                        .header("Authorization", bearerToken(PLAYER_AUTH_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authUserId").value(PLAYER_AUTH_USER_ID.toString()))
                .andExpect(jsonPath("$.role").value("PLAYER"));
    }

    @Test
    void insufficientRoleReturnsForbiddenContract() throws Exception {
        mockMvc.perform(get("/api/organizer/security-test")
                        .header("Authorization", bearerToken(PLAYER_AUTH_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void organizerRoleCanAccessOrganizerEndpoint() throws Exception {
        mockMvc.perform(get("/api/organizer/security-test")
                        .header("Authorization", bearerToken(ORGANIZER_AUTH_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("organizer"));
    }

    @Test
    void adminCanAccessOrganizerTournamentRouteThroughExplicitAllowance() throws Exception {
        mockMvc.perform(get("/api/organizer/tournaments/security-test")
                        .header("Authorization", bearerToken(ADMIN_AUTH_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("organizer tournament"));
    }

    @ParameterizedTest
    @CsvSource({
            "user_metadata, organizer",
            "user_metadata, admin",
            "user_metadata, captain",
            "app_metadata, organizer",
            "app_metadata, admin",
            "app_metadata, captain"
    })
    void playerDatabaseRoleCannotBeEscalatedByJwtMetadata(String metadataClaim, String desiredRole) throws Exception {
        String token = bearerTokenWithMetadata(PLAYER_AUTH_USER_ID, metadataClaim, desiredRole);

        mockMvc.perform(get("/api/me/security-test").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PLAYER"));
        mockMvc.perform(get("/api/organizer/security-test").header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/admin/security-test").header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void organizerTournamentRoutesRequireOrganizerOrAdminRole() throws Exception {
        mockMvc.perform(get("/api/organizer/tournaments/security-test")
                        .header("Authorization", bearerToken(PLAYER_AUTH_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void organizerCannotUsePlayerAnalyticsEndpoint() throws Exception {
        mockMvc.perform(get("/api/me/analytics")
                        .header("Authorization", bearerToken(ORGANIZER_AUTH_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void organizerCannotUsePlayerProgressAnalyticsEndpoint() throws Exception {
        mockMvc.perform(get("/api/me/analytics/progress")
                        .header("Authorization", bearerToken(ORGANIZER_AUTH_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void organizerCannotUsePlayerHeroAnalyticsEndpoint() throws Exception {
        mockMvc.perform(get("/api/me/analytics/heroes")
                        .header("Authorization", bearerToken(ORGANIZER_AUTH_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void organizerCannotUsePlayerHeroMasteryAnalyticsEndpoint() throws Exception {
        mockMvc.perform(get("/api/me/analytics/heroes/11111111-1111-4111-8111-111111111111/mastery")
                        .header("Authorization", bearerToken(ORGANIZER_AUTH_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void organizerCannotUsePlayerInsightsAnalyticsEndpoint() throws Exception {
        mockMvc.perform(get("/api/me/analytics/insights")
                        .header("Authorization", bearerToken(ORGANIZER_AUTH_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void playerCannotUseOrganizerAnalyticsEndpoint() throws Exception {
        mockMvc.perform(get("/api/organizer/analytics")
                        .header("Authorization", bearerToken(PLAYER_AUTH_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void matchImportCreateRequiresAdminRole() throws Exception {
        mockMvc.perform(post("/api/match-imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dotaMatchId\":\"7894561230\"}")
                        .header("Authorization", bearerToken(ORGANIZER_AUTH_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void organizerCannotUseTeamStorageUploadRoutes() throws Exception {
        mockMvc.perform(post("/api/teams/77777777-7777-4777-8777-777777777777/logo/upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "logo.png",
                                  "contentType": "image/png",
                                  "fileSizeBytes": 1024
                                }
                                """)
                        .header("Authorization", bearerToken(ORGANIZER_AUTH_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void steamSessionCookieCreatesCurrentUserContext() throws Exception {
        mockMvc.perform(get("/api/me/security-test")
                        .cookie(steamSessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(STEAM_PROFILE_ID.toString()))
                .andExpect(jsonPath("$.steamId").value(STEAM_ID))
                .andExpect(jsonPath("$.role").value("PLAYER"));
    }

    @Test
    void invalidSteamSessionCookieReturnsUnauthorizedContract() throws Exception {
        mockMvc.perform(get("/api/me/security-test")
                        .cookie(new Cookie("dotaops_steam_session", "invalid")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    @Test
    void invalidSteamSessionCookieDoesNotBlockPublicEndpoint() throws Exception {
        mockMvc.perform(get("/api/health")
                        .cookie(new Cookie("dotaops_steam_session", "invalid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    @Test
    void steamPlayerCannotAccessOrganizerEndpoint() throws Exception {
        mockMvc.perform(get("/api/organizer/security-test")
                        .cookie(steamSessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void bearerJwtKeepsPriorityOverSteamSessionCookie() throws Exception {
        mockMvc.perform(get("/api/organizer/security-test")
                        .cookie(new Cookie("dotaops_steam_session", "invalid"))
                        .header("Authorization", bearerToken(ORGANIZER_AUTH_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("organizer"));
    }

    @Test
    void corsAllowedOriginsComeFromConfiguration() {
        var configuration = corsConfigurationSource.getCorsConfiguration(
                new MockHttpServletRequest("GET", "/api/health"));

        org.assertj.core.api.Assertions.assertThat(configuration).isNotNull();
        org.assertj.core.api.Assertions.assertThat(configuration.getAllowedOriginPatterns())
                .containsExactly("https://app.example.test", "http://localhost:5173");
    }

    private static String bearerToken(UUID authUserId) throws Exception {
        return "Bearer " + SupabaseJwtTestSupport.token(authUserId, Instant.now());
    }

    private static String bearerTokenWithMetadata(
            UUID authUserId,
            String metadataClaim,
            String desiredRole
    ) throws Exception {
        return "Bearer " + SupabaseJwtTestSupport.tokenWithMetadata(
                authUserId,
                Instant.now(),
                metadataClaim,
                desiredRole);
    }

    private Cookie steamSessionCookie() {
        return new Cookie("dotaops_steam_session", steamSessionTokenService.createToken(new SteamAuthResult(
                STEAM_ID,
                STEAM_PROFILE_ID,
                UUID.fromString("77777777-7777-4777-8777-777777777777"),
                false,
                false,
                "Steam Player",
                null,
                "https://steamcommunity.com/profiles/" + STEAM_ID + "/",
                URI.create("http://localhost:3000/auth/steam/callback"))));
    }

    private static AuthenticatedProfile profile(UUID authUserId, ProfileRole role) {
        return new AuthenticatedProfile(
                UUID.nameUUIDFromBytes(authUserId.toString().getBytes(StandardCharsets.UTF_8)),
                authUserId,
                role.databaseValue(),
                role);
    }

    @RestController
    static class SecurityTestController {

        @GetMapping("/api/me/security-test")
        Map<String, Object> me(Authentication authentication) {
            SupabasePrincipal principal = (SupabasePrincipal) authentication.getPrincipal();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("authUserId", principal.authUserId() == null ? null : principal.authUserId().toString());
            response.put("profileId", principal.profile()
                    .map(AuthenticatedProfile::profileId)
                    .map(UUID::toString)
                    .orElse(null));
            response.put("steamId", principal.steamId());
            response.put("role", principal.role().name());
            return response;
        }

        @GetMapping("/api/organizer/security-test")
        Map<String, String> organizer() {
            return Map.of("result", "organizer");
        }

        @GetMapping("/api/organizer/tournaments/security-test")
        Map<String, String> organizerTournament() {
            return Map.of("result", "organizer tournament");
        }

        @GetMapping("/api/admin/security-test")
        Map<String, String> admin() {
            return Map.of("result", "admin");
        }

        @GetMapping("/api/public/tournaments/security-test")
        Map<String, String> publicTournaments() {
            return Map.of("result", "public");
        }

        @GetMapping("/api/public/analytics/security-test")
        Map<String, String> publicAnalytics() {
            return Map.of("result", "public analytics");
        }
    }

    @TestConfiguration
    static class SecurityTestRepositoryConfig {

        @Bean
        @Primary
        AuthenticatedProfileRepository authenticatedProfileRepository() {
            return Mockito.mock(AuthenticatedProfileRepository.class);
        }
    }
}
