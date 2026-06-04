package si.um.feri.dotaops.backend.analytics.web;

import java.math.BigDecimal;
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
import si.um.feri.dotaops.backend.analytics.service.AnalyticsQueryService;
import si.um.feri.dotaops.backend.auth.repository.AuthenticatedProfileRepository;
import si.um.feri.dotaops.backend.auth.service.SupabaseJwtTestSupport;
import si.um.feri.dotaops.backend.common.error.ResourceNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
        BackendApplication.class,
        PublicAnalyticsControllerTest.PublicAnalyticsControllerTestConfig.class
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
class PublicAnalyticsControllerTest {

    private static final UUID TOURNAMENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TEAM_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PROFILE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID HERO_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AnalyticsQueryService analyticsQueryService;

    @Autowired
    private AuthenticatedProfileRepository authenticatedProfileRepository;

    @BeforeEach
    void setUp() {
        Mockito.reset(analyticsQueryService, authenticatedProfileRepository);
        when(authenticatedProfileRepository.findByAuthUserId(Mockito.any())).thenReturn(Optional.empty());
    }

    @Test
    void playerMetricsWorkWithoutJwtAndSupportSnakeCaseFilters() throws Exception {
        when(analyticsQueryService.playerMetrics(any())).thenReturn(List.of(playerMetrics()));

        mockMvc.perform(get("/api/public/analytics/players")
                        .queryParam("tournament_id", TOURNAMENT_ID.toString())
                        .queryParam("profileId", PROFILE_ID.toString())
                        .queryParam("from", "2026-05-01T00:00:00Z")
                        .queryParam("to", "2026-06-01T00:00:00Z")
                        .queryParam("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].profileId").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.data[0].gamesPlayed").value(4))
                .andExpect(jsonPath("$.data[0].winRate").value(75.5))
                .andExpect(jsonPath("$.data[0].kda").value(5.25))
                .andExpect(jsonPath("$.data[0].avgGpm").value(620.25))
                .andExpect(jsonPath("$.data[0].rawResponse").doesNotExist())
                .andExpect(jsonPath("$.data[0].normalizedPayload").doesNotExist())
                .andExpect(jsonPath("$.data[0].rawPlayer").doesNotExist());

        ArgumentCaptor<AnalyticsFilters> filtersCaptor = ArgumentCaptor.forClass(AnalyticsFilters.class);
        verify(analyticsQueryService).playerMetrics(filtersCaptor.capture());
        assertThat(filtersCaptor.getValue().tournamentId()).isEqualTo(TOURNAMENT_ID);
        assertThat(filtersCaptor.getValue().profileId()).isEqualTo(PROFILE_ID);
        assertThat(filtersCaptor.getValue().from()).isEqualTo(OffsetDateTime.parse("2026-05-01T00:00:00Z"));
        assertThat(filtersCaptor.getValue().to()).isEqualTo(OffsetDateTime.parse("2026-06-01T00:00:00Z"));
        assertThat(filtersCaptor.getValue().limit()).isEqualTo(25);
    }

    @Test
    void teamMetricsReturnBasicAggregates() throws Exception {
        when(analyticsQueryService.teamMetrics(any())).thenReturn(List.of(teamMetrics()));

        mockMvc.perform(get("/api/public/analytics/teams")
                        .queryParam("teamId", TEAM_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].teamId").value(TEAM_ID.toString()))
                .andExpect(jsonPath("$.data[0].gamesPlayed").value(5))
                .andExpect(jsonPath("$.data[0].wins").value(3))
                .andExpect(jsonPath("$.data[0].avgKda").value(4.75))
                .andExpect(jsonPath("$.data[0].avgHeroDamage").value(18800.75));
    }

    @Test
    void heroMetricsReturnHeroIdentityAndAggregates() throws Exception {
        when(analyticsQueryService.heroMetrics(any())).thenReturn(List.of(heroMetrics()));

        mockMvc.perform(get("/api/public/analytics/heroes")
                        .queryParam("hero_id", HERO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].heroId").value(HERO_ID.toString()))
                .andExpect(jsonPath("$.data[0].dotaHeroId").value(1))
                .andExpect(jsonPath("$.data[0].localizedName").value("Anti-Mage"))
                .andExpect(jsonPath("$.data[0].gamesPlayed").value(7))
                .andExpect(jsonPath("$.data[0].kda").value(6.40));
    }

    @Test
    void tournamentMetricsReturnMostPickedHeroes() throws Exception {
        when(analyticsQueryService.tournamentMetrics(any(AnalyticsFilters.class))).thenReturn(List.of(tournamentMetrics()));

        mockMvc.perform(get("/api/public/analytics/tournaments")
                        .queryParam("team_id", TEAM_ID.toString())
                        .queryParam("heroId", HERO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tournamentId").value(TOURNAMENT_ID.toString()))
                .andExpect(jsonPath("$.data[0].gamesPlayed").value(8))
                .andExpect(jsonPath("$.data[0].teamsCount").value(4))
                .andExpect(jsonPath("$.data[0].avgKda").value(3.90))
                .andExpect(jsonPath("$.data[0].mostPickedHeroes[0].heroId").value(HERO_ID.toString()));
    }

    @Test
    void tournamentMetricsByIdReturnsSinglePublicTournamentMetric() throws Exception {
        when(analyticsQueryService.tournamentMetrics(TOURNAMENT_ID)).thenReturn(tournamentMetrics());

        mockMvc.perform(get("/api/public/analytics/tournaments/" + TOURNAMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tournamentId").value(TOURNAMENT_ID.toString()))
                .andExpect(jsonPath("$.data.rawResponse").doesNotExist())
                .andExpect(jsonPath("$.data.normalizedPayload").doesNotExist());
    }

    @Test
    void tournamentMetricsByIdDoesNotExposePrivateTournament() throws Exception {
        when(analyticsQueryService.tournamentMetrics(TOURNAMENT_ID))
                .thenThrow(new ResourceNotFoundException("Tournament analytics", "tournamentId", TOURNAMENT_ID));

        mockMvc.perform(get("/api/public/analytics/tournaments/" + TOURNAMENT_ID))
                .andExpect(status().isNotFound());
    }

    private static PlayerMetricsResponse playerMetrics() {
        return new PlayerMetricsResponse(
                PROFILE_ID,
                "Carry Player",
                TEAM_ID,
                "Radiant Five",
                TOURNAMENT_ID,
                "Mid Wars Open",
                4,
                3,
                1,
                new BigDecimal("75.50"),
                30,
                8,
                12,
                new BigDecimal("7.50"),
                new BigDecimal("2.00"),
                new BigDecimal("3.00"),
                new BigDecimal("5.25"),
                new BigDecimal("620.25"),
                new BigDecimal("710.50"),
                new BigDecimal("20500.00"));
    }

    private static TeamMetricsResponse teamMetrics() {
        return new TeamMetricsResponse(
                TEAM_ID,
                "Radiant Five",
                TOURNAMENT_ID,
                "Mid Wars Open",
                5,
                3,
                2,
                new BigDecimal("60.00"),
                120,
                80,
                150,
                new BigDecimal("24.00"),
                new BigDecimal("16.00"),
                new BigDecimal("30.00"),
                new BigDecimal("4.75"),
                new BigDecimal("540.00"),
                new BigDecimal("650.50"),
                new BigDecimal("18800.75"));
    }

    private static HeroMetricsResponse heroMetrics() {
        return new HeroMetricsResponse(
                HERO_ID,
                1,
                "npc_dota_hero_antimage",
                "Anti-Mage",
                "https://cdn.example.test/antimage.png",
                "https://cdn.example.test/icons/antimage.png",
                TOURNAMENT_ID,
                "Mid Wars Open",
                7,
                4,
                3,
                new BigDecimal("57.14"),
                52,
                15,
                44,
                new BigDecimal("7.43"),
                new BigDecimal("2.14"),
                new BigDecimal("6.29"),
                new BigDecimal("6.40"),
                new BigDecimal("615.20"),
                new BigDecimal("702.10"),
                new BigDecimal("22500.00"));
    }

    private static TournamentMetricsResponse tournamentMetrics() {
        return new TournamentMetricsResponse(
                TOURNAMENT_ID,
                "Mid Wars Open",
                8,
                4,
                20,
                16,
                2410,
                320,
                300,
                580,
                new BigDecimal("40.00"),
                new BigDecimal("3.90"),
                List.of(new PickedHeroMetricsResponse(
                        HERO_ID,
                        1,
                        "Anti-Mage",
                        "https://cdn.example.test/antimage.png",
                        "https://cdn.example.test/icons/antimage.png",
                        5)));
    }

    @TestConfiguration
    static class PublicAnalyticsControllerTestConfig {

        @Bean
        @Primary
        AnalyticsQueryService analyticsQueryService() {
            return Mockito.mock(AnalyticsQueryService.class);
        }

        @Bean
        @Primary
        AuthenticatedProfileRepository authenticatedProfileRepository() {
            return Mockito.mock(AuthenticatedProfileRepository.class);
        }
    }
}
