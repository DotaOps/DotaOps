package si.um.feri.dotaops.backend.tournament.web;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
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
import si.um.feri.dotaops.backend.common.pagination.PageMeta;
import si.um.feri.dotaops.backend.common.pagination.PageResponse;
import si.um.feri.dotaops.backend.tournament.domain.TournamentFormat;
import si.um.feri.dotaops.backend.tournament.dto.TournamentDetailResponse;
import si.um.feri.dotaops.backend.tournament.dto.TournamentPublicResponse;
import si.um.feri.dotaops.backend.tournament.dto.TournamentResponse;
import si.um.feri.dotaops.backend.tournament.dto.TournamentSettingsDto;
import si.um.feri.dotaops.backend.tournament.dto.UpdateTournamentRequest;
import si.um.feri.dotaops.backend.tournament.service.TournamentService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
        BackendApplication.class,
        TournamentControllerTest.TournamentControllerTestConfig.class
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
class TournamentControllerTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID PROFILE_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID TOURNAMENT_ID = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final OffsetDateTime STARTS_AT = OffsetDateTime.parse("2026-06-01T18:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TournamentService tournamentService;

    @Autowired
    private AuthenticatedProfileRepository authenticatedProfileRepository;

    @BeforeEach
    void setUp() {
        Mockito.reset(tournamentService, authenticatedProfileRepository);
        when(authenticatedProfileRepository.findByAuthUserId(AUTH_USER_ID))
                .thenReturn(Optional.of(authenticatedProfile()));
    }

    @Test
    void publicListWorksWithoutJwt() throws Exception {
        when(tournamentService.listPublicTournaments(isNull(), eq(0), eq(20))).thenReturn(new PageResponse<>(
                List.of(publicResponse()),
                new PageMeta(0, 20, 1, 1, false, false)));

        mockMvc.perform(get("/api/tournaments"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.items[0].id").value(TOURNAMENT_ID.toString()))
                .andExpect(jsonPath("$.data.items[0].slug").value("mid-wars-open"))
                .andExpect(jsonPath("$.data.items[0].status").value("published"))
                .andExpect(jsonPath("$.data.items[0].teamsCount").value(8))
                .andExpect(jsonPath("$.data.items[0].settings.teamSize").value(5))
                .andExpect(jsonPath("$.data.page.totalElements").value(1));
    }

    @Test
    void publicDetailWorksWithoutJwt() throws Exception {
        when(tournamentService.getPublicTournament("mid-wars-open")).thenReturn(detailResponse());

        mockMvc.perform(get("/api/tournaments/mid-wars-open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(TOURNAMENT_ID.toString()))
                .andExpect(jsonPath("$.data.rules").value("Default Dota 2 rules"))
                .andExpect(jsonPath("$.data.settings.format").value("single_elimination"));
    }

    @Test
    void organizerListRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/organizer/tournaments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void organizerListWorksForAuthenticatedUser() throws Exception {
        when(tournamentService.listOrganizerTournaments(isNull(), eq(0), eq(20))).thenReturn(new PageResponse<>(
                List.of(organizerResponse("draft")),
                new PageMeta(0, 20, 1, 1, false, false)));

        mockMvc.perform(get("/api/organizer/tournaments")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(TOURNAMENT_ID.toString()))
                .andExpect(jsonPath("$.data.items[0].status").value("draft"))
                .andExpect(jsonPath("$.data.items[0].organizerProfileId").value(PROFILE_ID.toString()));
    }

    @Test
    void createTournamentReturnsCreatedTournament() throws Exception {
        when(tournamentService.createTournament(any())).thenReturn(organizerResponse("draft"));

        mockMvc.perform(post("/api/organizer/tournaments")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Mid Wars Open",
                                  "startsAt": "2026-06-01T18:00:00Z",
                                  "settings": {
                                    "maxTeams": 8,
                                    "minTeams": 2,
                                    "teamSize": 5,
                                    "bestOf": 1,
                                    "format": "single_elimination",
                                    "checkInEnabled": false,
                                    "allowSubstitutes": true
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/organizer/tournaments/" + TOURNAMENT_ID))
                .andExpect(jsonPath("$.data.id").value(TOURNAMENT_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("draft"));
    }

    @Test
    void invalidCreatePayloadReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/organizer/tournaments")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "",
                                  "slug": "bad slug"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void updateTournamentReturnsUpdatedTournament() throws Exception {
        when(tournamentService.updateTournament(eq(TOURNAMENT_ID), any(UpdateTournamentRequest.class)))
                .thenReturn(organizerResponse("draft"));

        mockMvc.perform(patch("/api/organizer/tournaments/" + TOURNAMENT_ID)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Updated tournament description"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(TOURNAMENT_ID.toString()));
    }

    @Test
    void publishTournamentReturnsPublishedTournament() throws Exception {
        when(tournamentService.publishTournament(TOURNAMENT_ID)).thenReturn(organizerResponse("published"));

        mockMvc.perform(post("/api/organizer/tournaments/" + TOURNAMENT_ID + "/publish")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("published"));
    }

    @Test
    void archiveTournamentReturnsArchivedTournament() throws Exception {
        when(tournamentService.archiveTournament(TOURNAMENT_ID)).thenReturn(organizerResponse("archived"));

        mockMvc.perform(post("/api/organizer/tournaments/" + TOURNAMENT_ID + "/archive")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("archived"));
    }

    private static String bearerToken() throws Exception {
        return "Bearer " + SupabaseJwtTestSupport.token(AUTH_USER_ID, Instant.now());
    }

    private static AuthenticatedProfile authenticatedProfile() {
        return new AuthenticatedProfile(
                PROFILE_ID,
                AUTH_USER_ID,
                "Organizer",
                ProfileRole.ORGANIZER);
    }

    private static TournamentSettingsDto settings() {
        return new TournamentSettingsDto(
                8,
                2,
                5,
                1,
                TournamentFormat.SINGLE_ELIMINATION,
                false,
                true);
    }

    private static TournamentPublicResponse publicResponse() {
        return new TournamentPublicResponse(
                TOURNAMENT_ID,
                "mid-wars-open",
                "Mid Wars Open",
                "published",
                "single_elimination",
                STARTS_AT,
                STARTS_AT.plusDays(1),
                STARTS_AT.minusDays(7),
                STARTS_AT.minusDays(1),
                null,
                null,
                "UTC",
                8,
                8,
                3,
                "Organizer",
                "TBD",
                "Public qualifier",
                settings());
    }

    private static TournamentDetailResponse detailResponse() {
        return new TournamentDetailResponse(
                TOURNAMENT_ID,
                "mid-wars-open",
                "Mid Wars Open",
                "published",
                "single_elimination",
                STARTS_AT,
                STARTS_AT.plusDays(1),
                STARTS_AT.minusDays(7),
                STARTS_AT.minusDays(1),
                null,
                null,
                "UTC",
                8,
                8,
                3,
                "Organizer",
                "TBD",
                "Public qualifier",
                "Default Dota 2 rules",
                settings());
    }

    private static TournamentResponse organizerResponse(String status) {
        return new TournamentResponse(
                TOURNAMENT_ID,
                "mid-wars-open",
                "Mid Wars Open",
                status,
                "single_elimination",
                PROFILE_ID,
                "Organizer",
                "Public qualifier",
                "Default Dota 2 rules",
                "TBD",
                8,
                8,
                3,
                STARTS_AT,
                STARTS_AT.plusDays(1),
                STARTS_AT.minusDays(7),
                STARTS_AT.minusDays(1),
                null,
                null,
                "UTC",
                "published".equals(status),
                "published".equals(status) ? STARTS_AT.minusDays(10) : null,
                settings(),
                STARTS_AT.minusDays(20),
                STARTS_AT.minusDays(2));
    }

    @TestConfiguration
    static class TournamentControllerTestConfig {

        @Bean
        @Primary
        TournamentService tournamentService() {
            return Mockito.mock(TournamentService.class);
        }

        @Bean
        @Primary
        AuthenticatedProfileRepository authenticatedProfileRepository() {
            return Mockito.mock(AuthenticatedProfileRepository.class);
        }
    }
}
