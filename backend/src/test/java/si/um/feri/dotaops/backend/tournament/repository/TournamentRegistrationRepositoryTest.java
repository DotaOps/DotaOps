package si.um.feri.dotaops.backend.tournament.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import si.um.feri.dotaops.backend.tournament.domain.TournamentRegistration;

import static org.assertj.core.api.Assertions.assertThat;

class TournamentRegistrationRepositoryTest {

    private static final UUID REGISTRATION_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TOURNAMENT_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID TEAM_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID CAPTAIN_PROFILE_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

    private final TournamentRegistration registration = new TournamentRegistration(
            REGISTRATION_ID,
            TOURNAMENT_ID,
            null,
            null,
            TEAM_ID,
            null,
            null,
            null,
            CAPTAIN_PROFILE_ID,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    private final CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate(registration);
    private final TournamentRegistrationRepository repository = new TournamentRegistrationRepository(jdbcTemplate);

    @Test
    void rosterCountExcludesMembershipsWithLeftTimestamp() {
        repository.countActiveRosterMembers(TEAM_ID);

        assertThat(jdbcTemplate.typedQuerySql)
                .contains("is_active = true")
                .contains("left_at is null");
    }

    @Test
    void rosterSnapshotAndProfileCountUseCanonicalActiveMembership() {
        repository.create(new CreateTournamentRegistrationCommand(
                TOURNAMENT_ID,
                TEAM_ID,
                CAPTAIN_PROFILE_ID,
                null,
                null), 5);

        assertThat(jdbcTemplate.updateSql).isNotEmpty();
        assertThat(jdbcTemplate.updateSql.getFirst())
                .contains("tm.is_active = true")
                .contains("tm.left_at is null");
        assertThat(jdbcTemplate.typedQuerySql)
                .contains("tm.is_active = true")
                .contains("tm.left_at is null");
    }

    private static class CapturingJdbcTemplate extends JdbcTemplate {

        private final TournamentRegistration registration;
        private final List<String> updateSql = new ArrayList<>();
        private String typedQuerySql;

        private CapturingJdbcTemplate(TournamentRegistration registration) {
            this.registration = registration;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            return (T) registration;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            this.typedQuerySql = sql;
            if (requiredType == Integer.class) {
                return requiredType.cast(0);
            }

            throw new IllegalArgumentException("Unsupported captured result type: " + requiredType.getName());
        }

        @Override
        public int update(String sql, Object... args) {
            updateSql.add(sql);
            return 1;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            return List.of();
        }
    }
}
