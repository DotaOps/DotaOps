package si.um.feri.dotaops.backend.analytics.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import si.um.feri.dotaops.backend.analytics.domain.AnalyticsFilters;
import si.um.feri.dotaops.backend.analytics.domain.ContextWeightClassification;
import si.um.feri.dotaops.backend.analytics.domain.ContextWeightReason;
import si.um.feri.dotaops.backend.analytics.service.RoleBasedAnalyticsService;
import si.um.feri.dotaops.backend.auth.domain.AuthenticatedProfile;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.repository.AuthenticatedProfileRepository;
import si.um.feri.dotaops.backend.auth.service.SupabaseJwtTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
        BackendApplication.class,
        MeAnalyticsControllerTest.MeAnalyticsControllerTestConfig.class
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
class MeAnalyticsControllerTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID HERO_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID TEAM_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID TOURNAMENT_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID MATCH_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoleBasedAnalyticsService roleBasedAnalyticsService;

    @Autowired
    private AuthenticatedProfileRepository authenticatedProfileRepository;

    @BeforeEach
    void setUp() {
        Mockito.reset(roleBasedAnalyticsService, authenticatedProfileRepository);
        when(authenticatedProfileRepository.findByAuthUserId(AUTH_USER_ID))
                .thenReturn(Optional.of(new AuthenticatedProfile(
                        PROFILE_ID,
                        AUTH_USER_ID,
                        "Player",
                        ProfileRole.PLAYER)));
    }

    @Test
    void heroMasteryEndpointReturnsStructuredReportAndForwardsFilters() throws Exception {
        when(roleBasedAnalyticsService.currentPlayerHeroMastery(eq(HERO_ID), any()))
                .thenReturn(heroMasteryResponse());

        mockMvc.perform(get("/api/me/analytics/heroes/" + HERO_ID + "/mastery")
                        .queryParam("tournament_id", TOURNAMENT_ID.toString())
                        .queryParam("teamId", TEAM_ID.toString())
                        .queryParam("hero_id", HERO_ID.toString())
                        .queryParam("from", "2026-05-01T00:00:00Z")
                        .queryParam("to", "2026-06-01T00:00:00Z")
                        .queryParam("limit", "15")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileId").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.data.heroId").value(HERO_ID.toString()))
                .andExpect(jsonPath("$.data.heroName").value("Anti-Mage"))
                .andExpect(jsonPath("$.data.games").value(5))
                .andExpect(jsonPath("$.data.masteryVerdict").value("STRONG"))
                .andExpect(jsonPath("$.data.comparisonToPlayerOverallBaseline[0].metric").value("winRate"))
                .andExpect(jsonPath("$.data.contextSummary.averageContextWeight").value(0.85))
                .andExpect(jsonPath("$.data.recentMatches[0].contextClassification").value("ROUGH_GAME"))
                .andExpect(jsonPath("$.data.deterministicNotes[0].category").value("FIGHTING"));

        ArgumentCaptor<AnalyticsFilters> filters = ArgumentCaptor.forClass(AnalyticsFilters.class);
        verify(roleBasedAnalyticsService).currentPlayerHeroMastery(eq(HERO_ID), filters.capture());
        assertThat(filters.getValue().tournamentId()).isEqualTo(TOURNAMENT_ID);
        assertThat(filters.getValue().teamId()).isEqualTo(TEAM_ID);
        assertThat(filters.getValue().heroId()).isEqualTo(HERO_ID);
        assertThat(filters.getValue().from()).isEqualTo(OffsetDateTime.parse("2026-05-01T00:00:00Z"));
        assertThat(filters.getValue().to()).isEqualTo(OffsetDateTime.parse("2026-06-01T00:00:00Z"));
        assertThat(filters.getValue().limit()).isEqualTo(15);
    }

    private HeroMasteryResponse heroMasteryResponse() {
        return new HeroMasteryResponse(
                PROFILE_ID,
                HERO_ID,
                "Anti-Mage",
                5,
                4,
                1,
                new BigDecimal("80.00"),
                new BigDecimal("8.00"),
                new BigDecimal("2.00"),
                new BigDecimal("10.00"),
                new BigDecimal("8.00"),
                new BigDecimal("650.00"),
                new BigDecimal("720.00"),
                new BigDecimal("300.00"),
                new BigDecimal("14.00"),
                new BigDecimal("22000.00"),
                new BigDecimal("26000.00"),
                new BigDecimal("5200.00"),
                new BigDecimal("200.00"),
                new BigDecimal("23.00"),
                List.of(new HeroMasteryRecentMatchResponse(
                        MATCH_ID,
                        null,
                        "7894561230",
                        OffsetDateTime.parse("2026-05-20T18:30:00Z"),
                        HERO_ID,
                        "Anti-Mage",
                        true,
                        12,
                        3,
                        14,
                        new BigDecimal("8.67"),
                        640,
                        720,
                        320,
                        12,
                        21000,
                        28000,
                        5400,
                        0,
                        22,
                        2400,
                        "RADIANT",
                        38,
                        24,
                        "RADIANT",
                        new BigDecimal("0.85"),
                        ContextWeightClassification.ROUGH_GAME,
                        List.of(ContextWeightReason.HIGH_DEATHS),
                        "This match has rough-game context, so trend insights reduce its influence.")),
                new HeroMasteryRecentTrendResponse(
                        5,
                        3,
                        2,
                        List.of(new HeroMasteryTrendMetricResponse(
                                "KDA",
                                new BigDecimal("8.00"),
                                new BigDecimal("6.00"),
                                new BigDecimal("2.00"),
                                HeroMasteryComparisonDirection.BETTER,
                                "Recent KDA is trending better than the previous hero window.")),
                        "Recent trend compares the latest hero matches against the immediately previous hero window."),
                List.of(new HeroMasteryMetricComparisonResponse(
                        "winRate",
                        new BigDecimal("80.00"),
                        new BigDecimal("55.00"),
                        new BigDecimal("25.00"),
                        HeroMasteryComparisonDirection.BETTER,
                        "Hero winRate is better than the player's overall baseline.")),
                new HeroMasteryContextSummaryResponse(
                        new BigDecimal("0.85"),
                        1,
                        0,
                        0,
                        4,
                        "Raw match metrics are unchanged. Rough or stomp games are weighted less only for interpretation."),
                HeroMasteryVerdict.STRONG,
                List.of(new HeroMasteryNoteResponse(
                        HeroMasteryNoteCategory.FIGHTING,
                        HeroMasteryNoteSeverity.LOW,
                        "Your KDA on this hero is above your overall baseline.",
                        "KDA",
                        new BigDecimal("8.00"),
                        new BigDecimal("4.00"))));
    }

    private static String bearerToken() throws Exception {
        return "Bearer " + SupabaseJwtTestSupport.token(AUTH_USER_ID, Instant.now());
    }

    @TestConfiguration
    static class MeAnalyticsControllerTestConfig {

        @Bean
        @Primary
        RoleBasedAnalyticsService roleBasedAnalyticsService() {
            return Mockito.mock(RoleBasedAnalyticsService.class);
        }

        @Bean
        @Primary
        AuthenticatedProfileRepository authenticatedProfileRepository() {
            return Mockito.mock(AuthenticatedProfileRepository.class);
        }
    }
}
