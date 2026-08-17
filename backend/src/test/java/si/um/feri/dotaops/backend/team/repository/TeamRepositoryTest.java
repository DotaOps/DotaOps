package si.um.feri.dotaops.backend.team.repository;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;

class TeamRepositoryTest {

    private static final UUID PROFILE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID EXCLUDED_TEAM_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private final CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
    private final TeamRepository repository = new TeamRepository(jdbcTemplate);

    @Test
    void currentTeamRequiresCanonicalActivePlayerMembershipInsteadOfCaptainIdAlone() {
        repository.findCurrentTeamForProfile(PROFILE_ID);

        assertThat(jdbcTemplate.sql)
                .contains("where t.disbanded_at is null")
                .contains("and exists (")
                .contains("tm.is_active = true")
                .contains("tm.left_at is null")
                .contains("p.role = 'player'::public.dotaops_user_role")
                .doesNotContain("and (\n                            t.captain_profile_id = ?");
        assertThat(jdbcTemplate.parameters).containsExactly(PROFILE_ID, PROFILE_ID);
    }

    @Test
    void otherCurrentTeamCheckIgnoresStaleCaptainRelationship() {
        repository.existsCurrentTeamForProfileExcluding(PROFILE_ID, EXCLUDED_TEAM_ID);

        assertThat(jdbcTemplate.sql)
                .contains("tm.is_active = true")
                .contains("tm.left_at is null")
                .contains("p.role = 'player'::public.dotaops_user_role")
                .doesNotContain("t.captain_profile_id");
        assertThat(jdbcTemplate.parameters).containsExactly(EXCLUDED_TEAM_ID, EXCLUDED_TEAM_ID, PROFILE_ID);
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
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            this.sql = sql;
            this.parameters = args;
            return requiredType.cast(Boolean.FALSE);
        }
    }
}
