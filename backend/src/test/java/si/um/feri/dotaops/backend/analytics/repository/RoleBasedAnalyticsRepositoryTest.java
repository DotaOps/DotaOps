package si.um.feri.dotaops.backend.analytics.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import si.um.feri.dotaops.backend.analytics.domain.AnalyticsFilters;

import static org.assertj.core.api.Assertions.assertThat;

class RoleBasedAnalyticsRepositoryTest {

    private static final UUID PROFILE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-05-01T00:00:00Z");
    private static final OffsetDateTime TO = OffsetDateTime.parse("2026-06-01T00:00:00Z");

    private final CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
    private final RoleBasedAnalyticsRepository repository = new RoleBasedAnalyticsRepository(jdbcTemplate);

    @Test
    void organizerCountsQueryKeepsWhitespaceAroundTimestampExpression() {
        repository.findOrganizerCounts(
                PROFILE_ID,
                false,
                new AnalyticsFilters(null, null, null, null, FROM, TO, 10));

        assertThat(jdbcTemplate.sql)
                .contains("or coalesce(mg.started_at")
                .doesNotContain("orcoalesce");
        assertThat(jdbcTemplate.parameters)
                .contains(FROM, TO);
    }

    private static class CapturingJdbcTemplate extends JdbcTemplate {

        private String sql;
        private Object[] parameters;

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            this.sql = sql;
            this.parameters = args;
            return (T) new RoleBasedAnalyticsRepository.OrganizerAnalyticsCounts(0, 0, 0, 0, 0, 0);
        }
    }
}
