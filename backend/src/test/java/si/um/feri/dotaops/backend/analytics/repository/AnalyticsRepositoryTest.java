package si.um.feri.dotaops.backend.analytics.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import si.um.feri.dotaops.backend.analytics.domain.AnalyticsFilters;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsRepositoryTest {

    private static final UUID TOURNAMENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TEAM_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID OTHER_TEAM_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID PROFILE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID HERO_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-05-01T00:00:00Z");
    private static final OffsetDateTime TO = OffsetDateTime.parse("2026-06-01T00:00:00Z");

    private final CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
    private final AnalyticsRepository repository = new AnalyticsRepository(jdbcTemplate);

    @Test
    void playerMetricsQueryUsesPublicTournamentFilterAndBoundParameters() {
        repository.findPlayerMetrics(new AnalyticsFilters(
                TOURNAMENT_ID,
                TEAM_ID,
                PROFILE_ID,
                HERO_ID,
                250));

        assertThat(jdbcTemplate.sql).contains("t.is_public = true");
        assertThat(jdbcTemplate.sql).contains("m.tournament_id = ?");
        assertThat(jdbcTemplate.sql).contains("mp.team_id = ?");
        assertThat(jdbcTemplate.sql).contains("mp.profile_id = ?");
        assertThat(jdbcTemplate.sql).contains("mp.hero_id = ?");
        assertThat(jdbcTemplate.sql).doesNotContain("raw_response", "normalized_payload", "raw_player");
        assertThat(jdbcTemplate.parameters)
                .containsExactly(TOURNAMENT_ID, TEAM_ID, PROFILE_ID, HERO_ID, 100);
    }

    @Test
    void tournamentMetricsQueryUsesNormalizedTablesWithoutRawPayloadColumns() {
        repository.findTournamentMetrics(new AnalyticsFilters(null, null, null, null, 10));

        assertThat(jdbcTemplate.sql).contains("from public.match_players mp");
        assertThat(jdbcTemplate.sql).contains("join public.matches m");
        assertThat(jdbcTemplate.sql).contains("join public.tournaments t");
        assertThat(jdbcTemplate.sql).contains("where t.is_public = true");
        assertThat(jdbcTemplate.sql).doesNotContain("raw_response", "normalized_payload", "raw_player");
        assertThat(jdbcTemplate.parameters).containsExactly(10);
    }

    @Test
    void protectedPlayerMetricsDoNotRequirePublicTournamentVisibility() {
        repository.findProtectedPlayerMetrics(new AnalyticsFilters(null, null, PROFILE_ID, null, 10));

        assertThat(jdbcTemplate.sql).contains("where true");
        assertThat(jdbcTemplate.sql).doesNotContain("t.is_public = true");
        assertThat(jdbcTemplate.parameters).containsExactly(PROFILE_ID, 10);
    }

    @Test
    void metricsQuerySupportsInclusiveFromAndExclusiveToTimeFilters() {
        repository.findPlayerMetrics(new AnalyticsFilters(null, null, null, null, FROM, TO, 10));

        assertThat(jdbcTemplate.sql).contains("coalesce(mg.started_at");
        assertThat(jdbcTemplate.sql).contains(">= ?");
        assertThat(jdbcTemplate.sql).contains("< ?");
        assertThat(jdbcTemplate.parameters).containsExactly(FROM, TO, 10);
    }

    @Test
    void publicMetricQueriesUseLiveNormalizedTablesInsteadOfMaterializedViews() {
        AnalyticsFilters filters = new AnalyticsFilters(null, null, null, null, 10);

        repository.findPlayerMetrics(filters);
        assertLiveNormalizedAnalyticsQuery();

        repository.findTeamMetrics(filters);
        assertLiveNormalizedAnalyticsQuery();

        repository.findHeroMetrics(filters);
        assertLiveNormalizedAnalyticsQuery();

        repository.findTournamentMetrics(filters);
        assertLiveNormalizedAnalyticsQuery();
    }

    @Test
    void protectedRecentPlayerMatchesUseSingleProfileSubjectAndBoundFilters() {
        repository.findRecentMatchesForPlayer(
                PROFILE_ID,
                new AnalyticsFilters(TOURNAMENT_ID, TEAM_ID, PROFILE_ID, HERO_ID, FROM, TO, 25),
                false);

        assertThat(jdbcTemplate.sql).contains("where true");
        assertThat(jdbcTemplate.sql).contains("and mp.profile_id = ?");
        assertThat(jdbcTemplate.sql).contains("m.tournament_id = ?");
        assertThat(jdbcTemplate.sql).contains("mp.team_id = ?");
        assertThat(jdbcTemplate.sql).contains("mp.hero_id = ?");
        assertThat(jdbcTemplate.sql).contains("having count(distinct mp.profile_id");
        assertThat(jdbcTemplate.sql).contains(") = 1");
        assertThat(jdbcTemplate.sql).doesNotContain("t.is_public = true", "raw_response", "normalized_payload", "raw_player");
        assertThat(jdbcTemplate.parameters)
                .containsExactly(PROFILE_ID, TOURNAMENT_ID, TEAM_ID, HERO_ID, FROM, TO, 25);
    }

    @Test
    void protectedRecentTeamMatchesUseSingleTeamSubjectAndBoundFilters() {
        repository.findRecentMatchesForTeam(
                TEAM_ID,
                new AnalyticsFilters(TOURNAMENT_ID, TEAM_ID, PROFILE_ID, HERO_ID, FROM, TO, 25),
                false);

        assertThat(jdbcTemplate.sql).contains("where true");
        assertThat(jdbcTemplate.sql).contains("and mp.team_id = ?");
        assertThat(jdbcTemplate.sql).contains("m.tournament_id = ?");
        assertThat(jdbcTemplate.sql).contains("mp.profile_id = ?");
        assertThat(jdbcTemplate.sql).contains("mp.hero_id = ?");
        assertThat(jdbcTemplate.sql).contains("having count(distinct mp.team_id");
        assertThat(jdbcTemplate.sql).contains(") = 1");
        assertThat(jdbcTemplate.sql).doesNotContain("t.is_public = true", "raw_response", "normalized_payload", "raw_player");
        assertThat(jdbcTemplate.parameters)
                .containsExactly(TEAM_ID, TOURNAMENT_ID, PROFILE_ID, HERO_ID, FROM, TO, 25);
    }

    @Test
    void protectedComparedTeamHeroMetricsUseSelectedTeamsAndBoundFilters() {
        repository.findHeroMetricsForTeams(
                TEAM_ID,
                OTHER_TEAM_ID,
                new AnalyticsFilters(TOURNAMENT_ID, TEAM_ID, PROFILE_ID, HERO_ID, FROM, TO, 25),
                false);

        assertThat(jdbcTemplate.sql).contains("where true");
        assertThat(jdbcTemplate.sql).contains("and mp.team_id in (?, ?)");
        assertThat(jdbcTemplate.sql).contains("m.tournament_id = ?");
        assertThat(jdbcTemplate.sql).doesNotContain("mp.team_id = ?");
        assertThat(jdbcTemplate.sql).contains("mp.profile_id = ?");
        assertThat(jdbcTemplate.sql).contains("mp.hero_id = ?");
        assertThat(jdbcTemplate.sql).doesNotContain("t.is_public = true", "raw_response", "normalized_payload", "raw_player");
        assertThat(jdbcTemplate.parameters)
                .containsExactly(TEAM_ID, OTHER_TEAM_ID, TOURNAMENT_ID, PROFILE_ID, HERO_ID, FROM, TO, 25);
    }

    @Test
    void protectedPlayerProgressUsesCurrentProfileFiltersAndChronologicalRecentRows() {
        repository.findPlayerProgress(
                PROFILE_ID,
                new AnalyticsFilters(TOURNAMENT_ID, TEAM_ID, PROFILE_ID, HERO_ID, FROM, TO, 25),
                false);

        assertThat(jdbcTemplate.sql).contains("where true");
        assertThat(jdbcTemplate.sql).contains("mp.profile_id = ?");
        assertThat(jdbcTemplate.sql).contains("m.tournament_id = ?");
        assertThat(jdbcTemplate.sql).contains("mp.team_id = ?");
        assertThat(jdbcTemplate.sql).contains("mp.hero_id = ?");
        assertThat(jdbcTemplate.sql).contains("mp.gold_per_min");
        assertThat(jdbcTemplate.sql).contains("mp.xp_per_min");
        assertThat(jdbcTemplate.sql).contains("mp.hero_damage");
        assertThat(jdbcTemplate.sql).contains("mp.tower_damage");
        assertThat(jdbcTemplate.sql).contains("mp.hero_healing");
        assertThat(jdbcTemplate.sql).contains("mp.last_hits");
        assertThat(jdbcTemplate.sql).contains("mp.denies");
        assertThat(jdbcTemplate.sql).contains("mp.is_winner as won");
        assertThat(jdbcTemplate.sql).contains("order by played_at desc nulls last");
        assertThat(jdbcTemplate.sql).contains("order by played_at asc nulls last");
        assertThat(jdbcTemplate.sql).doesNotContain("t.is_public = true", "raw_response", "normalized_payload", "raw_player");
        assertThat(jdbcTemplate.parameters)
                .containsExactly(TOURNAMENT_ID, TEAM_ID, PROFILE_ID, HERO_ID, FROM, TO, 25);
    }

    private void assertLiveNormalizedAnalyticsQuery() {
        assertThat(jdbcTemplate.sql).contains("from public.match_players mp");
        assertThat(jdbcTemplate.sql).contains("join public.matches m");
        assertThat(jdbcTemplate.sql).doesNotContain(
                "mv_player_metrics",
                "mv_team_metrics",
                "mv_hero_metrics",
                "mv_tournament_metrics");
    }

    private static class CapturingJdbcTemplate extends JdbcTemplate {

        private String sql;
        private Object[] parameters;

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            this.sql = sql;
            this.parameters = args;
            return List.of();
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
            return query(sql, rowMapper, new Object[0]);
        }
    }
}
