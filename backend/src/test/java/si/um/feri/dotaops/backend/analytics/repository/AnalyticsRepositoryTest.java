package si.um.feri.dotaops.backend.analytics.repository;

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
    private static final UUID PROFILE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID HERO_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

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
