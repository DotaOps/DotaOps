package si.um.feri.dotaops.backend.opendota.web;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
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
import si.um.feri.dotaops.backend.opendota.domain.MatchImportStatus;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaErrorCode;
import si.um.feri.dotaops.backend.opendota.service.MatchImportService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
        BackendApplication.class,
        MatchImportControllerTest.MatchImportControllerTestConfig.class
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
class MatchImportControllerTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID IMPORT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final String DOTA_MATCH_ID = "7894561230";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-12T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MatchImportService matchImportService;

    @Autowired
    private AuthenticatedProfileRepository authenticatedProfileRepository;

    @BeforeEach
    void setUp() {
        Mockito.reset(matchImportService, authenticatedProfileRepository);
        when(authenticatedProfileRepository.findByAuthUserId(AUTH_USER_ID))
                .thenReturn(Optional.of(authenticatedProfile()));
    }

    @Test
    void postMatchImportReturnsLifecycleResponse() throws Exception {
        when(matchImportService.importMatch(any(CreateMatchImportRequest.class), anyString()))
                .thenReturn(response(MatchImportStatus.READY));

        mockMvc.perform(post("/api/match-imports")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dotaMatchId": "7894561230"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/match-imports/" + IMPORT_ID))
                .andExpect(jsonPath("$.data.id").value(IMPORT_ID.toString()))
                .andExpect(jsonPath("$.data.dotaMatchId").value(DOTA_MATCH_ID))
                .andExpect(jsonPath("$.data.status").value("ready"))
                .andExpect(jsonPath("$.data.events[0].eventType").value("queued"))
                .andExpect(jsonPath("$.data.rawResponse").doesNotExist())
                .andExpect(jsonPath("$.data.normalizedPayload").doesNotExist());
    }

    @Test
    void getMatchImportByIdReturnsStatusAndEvents() throws Exception {
        when(matchImportService.getImport(IMPORT_ID)).thenReturn(response(MatchImportStatus.ERROR));

        mockMvc.perform(get("/api/match-imports/" + IMPORT_ID)
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("error"))
                .andExpect(jsonPath("$.data.errorCode").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.data.events[0].errorCode").value("RATE_LIMITED"));
    }

    @Test
    void getMatchImportByDotaMatchIdReturnsStatus() throws Exception {
        when(matchImportService.getImportByDotaMatchId(DOTA_MATCH_ID)).thenReturn(response(MatchImportStatus.PROCESSING));

        mockMvc.perform(get("/api/match-imports/by-match/" + DOTA_MATCH_ID)
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("processing"));
    }

    @Test
    void getMatchImportEventsReturnsHistory() throws Exception {
        when(matchImportService.getImportEvents(IMPORT_ID)).thenReturn(events(MatchImportStatus.QUEUED));

        mockMvc.perform(get("/api/match-imports/" + IMPORT_ID + "/events")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].eventType").value("queued"));
    }

    @Test
    void retryMatchImportReturnsSameImportId() throws Exception {
        when(matchImportService.retryImport(eq(IMPORT_ID), anyString())).thenReturn(response(MatchImportStatus.READY));

        mockMvc.perform(post("/api/match-imports/" + IMPORT_ID + "/retry")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(IMPORT_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("ready"));
    }

    private static String bearerToken() throws Exception {
        return "Bearer " + SupabaseJwtTestSupport.token(AUTH_USER_ID, Instant.now());
    }

    private static AuthenticatedProfile authenticatedProfile() {
        return new AuthenticatedProfile(
                PROFILE_ID,
                AUTH_USER_ID,
                "Admin",
                ProfileRole.ADMIN);
    }

    private static MatchImportResponse response(MatchImportStatus status) {
        return new MatchImportResponse(
                IMPORT_ID,
                null,
                null,
                DOTA_MATCH_ID,
                status,
                status == MatchImportStatus.ERROR ? OpenDotaErrorCode.RATE_LIMITED : null,
                status == MatchImportStatus.ERROR ? "OpenDota rate limit exceeded." : null,
                NOW,
                status == MatchImportStatus.PROCESSING ? null : NOW.plusSeconds(5),
                NOW,
                NOW.plusSeconds(5),
                events(eventTypeFor(status)));
    }

    private static MatchImportStatus eventTypeFor(MatchImportStatus status) {
        if (status == MatchImportStatus.ERROR) {
            return MatchImportStatus.ERROR;
        }

        return status == MatchImportStatus.PROCESSING ? MatchImportStatus.PROCESSING : MatchImportStatus.QUEUED;
    }

    private static List<MatchImportEventResponse> events(MatchImportStatus eventType) {
        return List.of(new MatchImportEventResponse(
                UUID.fromString("44444444-4444-4444-8444-444444444444"),
                eventType,
                eventType.databaseValue(),
                eventType == MatchImportStatus.ERROR ? OpenDotaErrorCode.RATE_LIMITED : null,
                NOW));
    }

    @TestConfiguration
    static class MatchImportControllerTestConfig {

        @Bean
        @Primary
        MatchImportService matchImportService() {
            return Mockito.mock(MatchImportService.class);
        }

        @Bean
        @Primary
        AuthenticatedProfileRepository authenticatedProfileRepository() {
            return Mockito.mock(AuthenticatedProfileRepository.class);
        }
    }
}
