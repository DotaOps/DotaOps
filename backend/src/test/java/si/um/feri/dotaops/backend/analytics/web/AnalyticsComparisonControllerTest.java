package si.um.feri.dotaops.backend.analytics.web;

import java.math.BigDecimal;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import si.um.feri.dotaops.backend.BackendApplication;
import si.um.feri.dotaops.backend.analytics.service.AnalyticsComparisonService;
import si.um.feri.dotaops.backend.auth.domain.AuthenticatedProfile;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.repository.AuthenticatedProfileRepository;
import si.um.feri.dotaops.backend.auth.service.SupabaseJwtTestSupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
        BackendApplication.class,
        AnalyticsComparisonControllerTest.AnalyticsComparisonControllerTestConfig.class
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
class AnalyticsComparisonControllerTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PROFILE_A_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PROFILE_B_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID HERO_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID MATCH_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AnalyticsComparisonService analyticsComparisonService;

    @Autowired
    private AuthenticatedProfileRepository authenticatedProfileRepository;

    @BeforeEach
    void setUp() {
        Mockito.reset(analyticsComparisonService, authenticatedProfileRepository);
        when(authenticatedProfileRepository.findByAuthUserId(AUTH_USER_ID))
                .thenReturn(Optional.of(new AuthenticatedProfile(
                        PROFILE_A_ID,
                        AUTH_USER_ID,
                        "Player",
                        ProfileRole.PLAYER)));
    }

    @Test
    void comparePlayersReturnsExtendedComparisonFields() throws Exception {
        when(analyticsComparisonService.comparePlayers(eq(PROFILE_A_ID), eq(PROFILE_B_ID), any()))
                .thenReturn(comparisonResponse());

        mockMvc.perform(get("/api/analytics/compare/players")
                        .queryParam("profileAId", PROFILE_A_ID.toString())
                        .queryParam("profileBId", PROFILE_B_ID.toString())
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileAId").value(PROFILE_A_ID.toString()))
                .andExpect(jsonPath("$.data.headlineComparison.profileA.avgNetWorth").value(20500.0))
                .andExpect(jsonPath("$.data.headlineComparison.delta.avgGpm").value(40.0))
                .andExpect(jsonPath("$.data.sharedHeroComparisons[0].heroName").value("Anti-Mage"))
                .andExpect(jsonPath("$.data.sharedHeroComparisons[0].delta.kda").value(1.1))
                .andExpect(jsonPath("$.data.enrichedMatchHistory[0].profileA.heroName").value("Anti-Mage"))
                .andExpect(jsonPath("$.data.warnings[0].code").value("LOW_SHARED_HERO_SAMPLE"));
    }

    @Test
    void playerCandidatesAcceptQAliasAndReturnAutocompleteFieldsWithoutSensitiveData() throws Exception {
        when(analyticsComparisonService.playerComparisonCandidates(eq("Aegis"), any()))
                .thenReturn(new PlayerComparisonLookupResponse(
                        "Aegis",
                        false,
                        false,
                        List.of(new PlayerComparisonCandidateResponse(
                                PROFILE_B_ID,
                                "Aegis Ace",
                                "aegis_ace",
                                null,
                                "Radiant Wolves",
                                "https://cdn.example.test/avatar.png",
                                123456789L,
                                8,
                                true,
                                "8 imported analytics matches"))));

        mockMvc.perform(get("/api/analytics/compare/players/candidates")
                        .queryParam("q", "Aegis")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.query").value("Aegis"))
                .andExpect(jsonPath("$.data.candidates[0].profileId").value(PROFILE_B_ID.toString()))
                .andExpect(jsonPath("$.data.candidates[0].displayName").value("Aegis Ace"))
                .andExpect(jsonPath("$.data.candidates[0].nickname").value("aegis_ace"))
                .andExpect(jsonPath("$.data.candidates[0].avatarUrl").value("https://cdn.example.test/avatar.png"))
                .andExpect(jsonPath("$.data.candidates[0].opendotaAccountId").value(123456789))
                .andExpect(jsonPath("$.data.candidates[0].analyticsGamesCount").value(8))
                .andExpect(jsonPath("$.data.candidates[0].hasAnalyticsData").value(true))
                .andExpect(jsonPath("$.data.candidates[0].label").value("8 imported analytics matches"))
                .andExpect(jsonPath("$.data.candidates[0].email").doesNotExist())
                .andExpect(jsonPath("$.data.candidates[0].authUserId").doesNotExist())
                .andExpect(jsonPath("$.data.candidates[0].userId").doesNotExist());
    }

    @Test
    void playerCandidatesAcceptSearchAliasAndReturnStableEmptyResponse() throws Exception {
        when(analyticsComparisonService.playerComparisonCandidates(eq("Nope"), any()))
                .thenReturn(new PlayerComparisonLookupResponse("Nope", false, false, List.of()));

        mockMvc.perform(get("/api/analytics/compare/players/candidates")
                        .queryParam("search", "Nope")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.query").value("Nope"))
                .andExpect(jsonPath("$.data.exactMatch").value(false))
                .andExpect(jsonPath("$.data.ambiguous").value(false))
                .andExpect(jsonPath("$.data.candidates").isArray())
                .andExpect(jsonPath("$.data.candidates").isEmpty());
    }

    private static PlayerComparisonResponse comparisonResponse() {
        PlayerComparisonMetricResponse profileA = metric(PROFILE_A_ID, "Carry One", "620.00", "5.50");
        PlayerComparisonMetricResponse profileB = metric(PROFILE_B_ID, "Carry Two", "580.00", "4.40");

        return new PlayerComparisonResponse(
                PROFILE_A_ID,
                PROFILE_B_ID,
                new AnalyticsComparisonFiltersResponse(null, null, null, null, null, null, 10, "public"),
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new PlayerComparisonHeadlineResponse(
                        profileA,
                        profileB,
                        new PlayerComparisonMetricDeltaResponse(
                                1,
                                1,
                                0,
                                new BigDecimal("5.00"),
                                new BigDecimal("1.10"),
                                new BigDecimal("1.00"),
                                new BigDecimal("-0.50"),
                                new BigDecimal("2.00"),
                                new BigDecimal("40.00"),
                                new BigDecimal("25.00"),
                                new BigDecimal("30.00"),
                                new BigDecimal("3.00"),
                                new BigDecimal("800.00"),
                                new BigDecimal("2500.00"),
                                new BigDecimal("600.00"),
                                new BigDecimal("40.00"))),
                List.of(heroDetail(4)),
                List.of(heroDetail(2)),
                List.of(new PlayerComparisonSharedHeroResponse(
                        HERO_ID,
                        1,
                        "Anti-Mage",
                        heroStats(PROFILE_A_ID, 4, "5.20"),
                        heroStats(PROFILE_B_ID, 2, "4.10"),
                        new PlayerComparisonHeroDeltaResponse(
                                2,
                                new BigDecimal("20.00"),
                                new BigDecimal("1.10"),
                                new BigDecimal("-0.50"),
                                new BigDecimal("50.00"),
                                new BigDecimal("30.00"),
                                new BigDecimal("3000.00"),
                                new BigDecimal("600.00")))),
                List.of(comparisonMatch()),
                List.of(new PlayerComparisonWarningResponse(
                        "LOW_SHARED_HERO_SAMPLE",
                        "WARNING",
                        "Shared hero comparison has a small sample.",
                        null,
                        HERO_ID,
                        "sharedHeroes",
                        2,
                        3)));
    }

    private static PlayerComparisonMetricResponse metric(UUID profileId, String displayName, String avgGpm, String kda) {
        return new PlayerComparisonMetricResponse(
                profileId,
                displayName,
                8,
                6,
                2,
                new BigDecimal("75.00"),
                new BigDecimal(kda),
                new BigDecimal("8.00"),
                new BigDecimal("2.00"),
                new BigDecimal("10.00"),
                new BigDecimal(avgGpm),
                new BigDecimal("700.00"),
                new BigDecimal("210.00"),
                new BigDecimal("12.00"),
                new BigDecimal("20500.00"),
                new BigDecimal("23000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("200.00"));
    }

    private static PlayerHeroPerformanceResponse heroDetail(int matches) {
        return new PlayerHeroPerformanceResponse(
                HERO_ID,
                1,
                "Anti-Mage",
                matches,
                Math.max(matches - 1, 0),
                Math.min(matches, 1),
                new BigDecimal("75.00"),
                new BigDecimal("8.00"),
                new BigDecimal("2.50"),
                new BigDecimal("10.00"),
                new BigDecimal("5.20"),
                new BigDecimal("610.00"),
                new BigDecimal("720.00"),
                new BigDecimal("22000.00"),
                new BigDecimal("2400.00"),
                new BigDecimal("120.00"),
                new BigDecimal("210.00"),
                new BigDecimal("10.00"),
                MATCH_ID,
                null,
                "7777777777",
                OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                MATCH_ID,
                null,
                "7777777777",
                OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                new BigDecimal("9.00"));
    }

    private static PlayerComparisonHeroStatsResponse heroStats(UUID profileId, int games, String kda) {
        return new PlayerComparisonHeroStatsResponse(
                profileId,
                games,
                Math.max(games - 1, 0),
                Math.min(games, 1),
                new BigDecimal("75.00"),
                new BigDecimal(kda),
                new BigDecimal("8.00"),
                new BigDecimal("2.50"),
                new BigDecimal("10.00"),
                new BigDecimal("610.00"),
                new BigDecimal("720.00"),
                new BigDecimal("22000.00"),
                new BigDecimal("2400.00"),
                new BigDecimal("120.00"));
    }

    private static PlayerComparisonMatchResponse comparisonMatch() {
        return new PlayerComparisonMatchResponse(
                MATCH_ID,
                null,
                "7777777777",
                null,
                null,
                OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                "RADIANT",
                new PlayerComparisonMatchPlayerResponse(
                        PROFILE_A_ID,
                        null,
                        null,
                        HERO_ID,
                        1,
                        "Anti-Mage",
                        true,
                        10,
                        2,
                        8,
                        new BigDecimal("9.00"),
                        640,
                        720,
                        240,
                        12,
                        21400,
                        24000,
                        2600,
                        0,
                        "RADIANT"),
                new PlayerComparisonMatchPlayerResponse(
                        PROFILE_B_ID,
                        null,
                        null,
                        HERO_ID,
                        1,
                        "Anti-Mage",
                        false,
                        7,
                        4,
                        9,
                        new BigDecimal("4.00"),
                        560,
                        660,
                        180,
                        8,
                        17200,
                        19000,
                        1800,
                        0,
                        "DIRE"));
    }

    private static String bearerToken() throws Exception {
        return "Bearer " + SupabaseJwtTestSupport.token(AUTH_USER_ID, Instant.now());
    }

    @TestConfiguration
    static class AnalyticsComparisonControllerTestConfig {

        @Bean
        @Primary
        AnalyticsComparisonService analyticsComparisonService() {
            return Mockito.mock(AnalyticsComparisonService.class);
        }

        @Bean
        @Primary
        AuthenticatedProfileRepository authenticatedProfileRepository() {
            return Mockito.mock(AuthenticatedProfileRepository.class);
        }
    }
}
