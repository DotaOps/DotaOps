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
import si.um.feri.dotaops.backend.tournament.dto.CreateTournamentRegistrationRequest;
import si.um.feri.dotaops.backend.tournament.dto.ReviewTournamentRegistrationRequest;
import si.um.feri.dotaops.backend.tournament.dto.TournamentRegistrationResponse;
import si.um.feri.dotaops.backend.tournament.service.TournamentRegistrationService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
        BackendApplication.class,
        TournamentRegistrationControllerTest.TournamentRegistrationControllerTestConfig.class
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
class TournamentRegistrationControllerTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID TOURNAMENT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID TEAM_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID REGISTRATION_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-06-01T18:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TournamentRegistrationService registrationService;

    @Autowired
    private AuthenticatedProfileRepository authenticatedProfileRepository;

    @BeforeEach
    void setUp() {
        Mockito.reset(registrationService, authenticatedProfileRepository);
        when(authenticatedProfileRepository.findByAuthUserId(AUTH_USER_ID))
                .thenReturn(Optional.of(authenticatedProfile()));
    }

    @Test
    void registerTeamRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/tournaments/" + TOURNAMENT_ID + "/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "teamId": "%s"
                                }
                                """.formatted(TEAM_ID)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void registerTeamReturnsCreatedRegistration() throws Exception {
        when(registrationService.registerTeam(eq(TOURNAMENT_ID), any(CreateTournamentRegistrationRequest.class)))
                .thenReturn(response("pending"));

        mockMvc.perform(post("/api/tournaments/" + TOURNAMENT_ID + "/registrations")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "teamId": "%s",
                                  "message": "Ready",
                                  "contactEmail": "captain@example.test"
                                }
                                """.formatted(TEAM_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/tournaments/" + TOURNAMENT_ID + "/registrations/" + REGISTRATION_ID))
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.teamId").value(TEAM_ID.toString()));
    }

    @Test
    void teamRegistrationStatusRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/teams/" + TEAM_ID + "/tournament-registrations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void organizerApproveRouteDelegatesToService() throws Exception {
        when(authenticatedProfileRepository.findByAuthUserId(AUTH_USER_ID))
                .thenReturn(Optional.of(authenticatedProfile(ProfileRole.ORGANIZER)));
        when(registrationService.approveRegistration(
                eq(TOURNAMENT_ID),
                eq(REGISTRATION_ID),
                any(ReviewTournamentRegistrationRequest.class)))
                .thenReturn(response("approved"));

        mockMvc.perform(post("/api/organizer/tournaments/" + TOURNAMENT_ID
                        + "/registrations/" + REGISTRATION_ID + "/approve")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "seedNumber": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("approved"));
    }

    private static String bearerToken() throws Exception {
        return "Bearer " + SupabaseJwtTestSupport.token(AUTH_USER_ID, Instant.now());
    }

    private static AuthenticatedProfile authenticatedProfile() {
        return authenticatedProfile(ProfileRole.PLAYER);
    }

    private static AuthenticatedProfile authenticatedProfile(ProfileRole role) {
        return new AuthenticatedProfile(
                PROFILE_ID,
                AUTH_USER_ID,
                role == ProfileRole.ORGANIZER ? "Organizer" : "Captain",
                role);
    }

    private static TournamentRegistrationResponse response(String status) {
        return new TournamentRegistrationResponse(
                REGISTRATION_ID,
                TOURNAMENT_ID,
                "mid-wars-open",
                "Mid Wars Open",
                TEAM_ID,
                "Radiant Five",
                "R5",
                "radiant-five",
                PROFILE_ID,
                "Captain",
                status,
                "Ready",
                null,
                null,
                null,
                null,
                null,
                "captain@example.test",
                List.of(),
                NOW.minusHours(1),
                NOW);
    }

    @TestConfiguration
    static class TournamentRegistrationControllerTestConfig {

        @Bean
        @Primary
        TournamentRegistrationService registrationService() {
            return Mockito.mock(TournamentRegistrationService.class);
        }

        @Bean
        @Primary
        AuthenticatedProfileRepository authenticatedProfileRepository() {
            return Mockito.mock(AuthenticatedProfileRepository.class);
        }
    }
}
