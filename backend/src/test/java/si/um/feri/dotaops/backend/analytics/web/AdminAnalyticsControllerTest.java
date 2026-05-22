package si.um.feri.dotaops.backend.analytics.web;

import java.time.Instant;
import java.time.OffsetDateTime;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import si.um.feri.dotaops.backend.BackendApplication;
import si.um.feri.dotaops.backend.analytics.service.AnalyticsRefreshService;
import si.um.feri.dotaops.backend.auth.domain.AuthenticatedProfile;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.repository.AuthenticatedProfileRepository;
import si.um.feri.dotaops.backend.auth.service.SupabaseJwtTestSupport;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
        BackendApplication.class,
        AdminAnalyticsControllerTest.AdminAnalyticsControllerTestConfig.class
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
class AdminAnalyticsControllerTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-21T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AnalyticsRefreshService analyticsRefreshService;

    @Autowired
    private AuthenticatedProfileRepository authenticatedProfileRepository;

    @BeforeEach
    void setUp() {
        Mockito.reset(analyticsRefreshService, authenticatedProfileRepository);
        when(authenticatedProfileRepository.findByAuthUserId(AUTH_USER_ID))
                .thenReturn(Optional.of(authenticatedProfile(ProfileRole.ADMIN)));
    }

    @Test
    void adminCanRunAnalyticsRefresh() throws Exception {
        when(analyticsRefreshService.refreshNow("admin request")).thenReturn(new AnalyticsRefreshResponse(
                "COMPLETED",
                "admin request",
                NOW,
                NOW.plusSeconds(1),
                1000L,
                "Analytics refresh completed."));

        mockMvc.perform(post("/api/admin/analytics/refresh")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.reason").value("admin request"))
                .andExpect(jsonPath("$.data.durationMs").value(1000));
    }

    @Test
    void nonAdminCannotRunAnalyticsRefresh() throws Exception {
        when(authenticatedProfileRepository.findByAuthUserId(AUTH_USER_ID))
                .thenReturn(Optional.of(authenticatedProfile(ProfileRole.ORGANIZER)));

        mockMvc.perform(post("/api/admin/analytics/refresh")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isForbidden());
    }

    private static String bearerToken() throws Exception {
        return "Bearer " + SupabaseJwtTestSupport.token(AUTH_USER_ID, Instant.now());
    }

    private static AuthenticatedProfile authenticatedProfile(ProfileRole role) {
        return new AuthenticatedProfile(
                PROFILE_ID,
                AUTH_USER_ID,
                "Admin",
                role);
    }

    @TestConfiguration
    static class AdminAnalyticsControllerTestConfig {

        @Bean
        @Primary
        AnalyticsRefreshService analyticsRefreshService() {
            return Mockito.mock(AnalyticsRefreshService.class);
        }

        @Bean
        @Primary
        AuthenticatedProfileRepository authenticatedProfileRepository() {
            return Mockito.mock(AuthenticatedProfileRepository.class);
        }
    }
}
