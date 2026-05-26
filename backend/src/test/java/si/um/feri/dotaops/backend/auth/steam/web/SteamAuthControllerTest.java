package si.um.feri.dotaops.backend.auth.steam.web;

import java.net.URI;
import java.time.OffsetDateTime;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import si.um.feri.dotaops.backend.BackendApplication;
import si.um.feri.dotaops.backend.auth.service.SupabaseJwtTestSupport;
import si.um.feri.dotaops.backend.auth.steam.domain.SteamAuthResult;
import si.um.feri.dotaops.backend.auth.steam.service.SteamAuthService;
import si.um.feri.dotaops.backend.auth.steam.service.SteamSessionCookieService;
import si.um.feri.dotaops.backend.auth.steam.service.SteamSessionTokenService;
import si.um.feri.dotaops.backend.common.error.RateLimitExceededException;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
        BackendApplication.class,
        SteamAuthControllerTest.SteamAuthControllerTestConfig.class
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "dotaops.supabase.auth.jwt-secret=" + SupabaseJwtTestSupport.SECRET,
        "dotaops.supabase.auth.issuer=" + SupabaseJwtTestSupport.ISSUER,
        "dotaops.supabase.auth.audience=" + SupabaseJwtTestSupport.AUDIENCE
})
class SteamAuthControllerTest {

    private static final UUID PROFILE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID EXTERNAL_ACCOUNT_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String STEAM_ID = "76561198000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SteamAuthService steamAuthService;

    @Autowired
    private SteamSessionTokenService steamSessionTokenService;

    @Autowired
    private SteamSessionCookieService steamSessionCookieService;

    @BeforeEach
    void setUp() {
        Mockito.reset(steamAuthService, steamSessionTokenService, steamSessionCookieService);
        when(steamSessionTokenService.createToken(any())).thenReturn("session.jwt");
        when(steamSessionCookieService.createSessionCookie("session.jwt")).thenReturn(ResponseCookie
                .from("dotaops_steam_session", "session.jwt")
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .build());
        when(steamSessionCookieService.clearSessionCookie()).thenReturn(ResponseCookie
                .from("dotaops_steam_session", "")
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .maxAge(0)
                .build());
    }

    @Test
    void loginRedirectsToSteamWithoutJwt() throws Exception {
        when(steamAuthService.beginLogin(any())).thenReturn(URI.create(
                "https://steamcommunity.com/openid/login?openid.mode=checkid_setup"));

        mockMvc.perform(get("/api/auth/steam/login"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://steamcommunity.com/openid/login?openid.mode=checkid_setup"));
    }

    @Test
    void loginReturnsRateLimitContract() throws Exception {
        when(steamAuthService.beginLogin(any()))
                .thenThrow(new RateLimitExceededException("Too many Steam login attempts from this IP address. Try again later."));

        mockMvc.perform(get("/api/auth/steam/login"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.path").value("/api/auth/steam/login"));
    }

    @Test
    void profileLinkRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/steam/link"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(steamAuthService, never()).beginProfileLink(any());
    }

    @Test
    void callbackReturnsJsonAuthResultWhenNoFrontendRedirectIsConfigured() throws Exception {
        when(steamAuthService.completeCallback(any())).thenReturn(authResult(null));

        mockMvc.perform(get("/api/auth/steam/callback")
                        .param("state", "state")
                        .param("openid.mode", "id_res"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, "dotaops_steam_session=session.jwt; Path=/; HttpOnly; SameSite=Lax"))
                .andExpect(jsonPath("$.data.status").value("authenticated"))
                .andExpect(jsonPath("$.data.steamId").value(STEAM_ID))
                .andExpect(jsonPath("$.data.profileId").value(PROFILE_ID.toString()));
    }

    @Test
    void callbackRedirectsToFrontendWhenServiceReturnsRedirectUri() throws Exception {
        URI redirectUri = URI.create("http://localhost:3000/auth/steam/callback?steamLogin=success");
        when(steamAuthService.completeCallback(any())).thenReturn(authResult(redirectUri));

        mockMvc.perform(get("/api/auth/steam/callback")
                        .param("state", "state")
                        .param("openid.mode", "id_res"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, "dotaops_steam_session=session.jwt; Path=/; HttpOnly; SameSite=Lax"))
                .andExpect(header().string("Location", redirectUri.toString()));
    }

    @Test
    void logoutClearsSteamSessionCookie() throws Exception {
        mockMvc.perform(post("/api/auth/steam/logout"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("dotaops_steam_session=;"),
                        containsString("Path=/"),
                        containsString("Max-Age=0"),
                        containsString("HttpOnly"),
                        containsString("SameSite=Lax"))))
                .andExpect(jsonPath("$.data.status").value("logged_out"));
    }

    @Test
    void invalidCallbackReturnsUnauthorizedContract() throws Exception {
        when(steamAuthService.completeCallback(any()))
                .thenThrow(new BadCredentialsException("Steam OpenID assertion is invalid."));

        mockMvc.perform(get("/api/auth/steam/callback")
                        .param("state", "state")
                        .param("openid.mode", "id_res"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/auth/steam/callback"));
    }

    private static SteamAuthResult authResult(URI redirectUri) {
        return new SteamAuthResult(
                STEAM_ID,
                PROFILE_ID,
                EXTERNAL_ACCOUNT_ID,
                true,
                true,
                "Dota Player",
                "https://cdn.example.test/avatar.png",
                "https://steamcommunity.com/profiles/" + STEAM_ID + "/",
                redirectUri);
    }

    @TestConfiguration
    static class SteamAuthControllerTestConfig {

        @Bean
        @Primary
        SteamAuthService steamAuthService() {
            return Mockito.mock(SteamAuthService.class);
        }

        @Bean
        @Primary
        SteamSessionTokenService steamSessionTokenService() {
            return Mockito.mock(SteamSessionTokenService.class);
        }

        @Bean
        @Primary
        SteamSessionCookieService steamSessionCookieService() {
            return Mockito.mock(SteamSessionCookieService.class);
        }
    }
}
