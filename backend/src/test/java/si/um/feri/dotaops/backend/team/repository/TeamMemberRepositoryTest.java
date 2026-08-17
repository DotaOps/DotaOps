package si.um.feri.dotaops.backend.team.repository;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import si.um.feri.dotaops.backend.team.domain.TeamMemberRole;

import static org.assertj.core.api.Assertions.assertThat;

class TeamMemberRepositoryTest {

    private static final UUID TEAM_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID MEMBER_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    private final CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
    private final TeamMemberRepository repository = new TeamMemberRepository(jdbcTemplate);

    @Test
    void activeMembershipReadsRequireActiveFlagAndNoLeftTimestamp() {
        repository.findActiveByTeamId(TEAM_ID);
        assertCanonicalActivePredicate();

        repository.findActiveByTeamAndProfile(TEAM_ID, PROFILE_ID);
        assertCanonicalActivePredicate();

        repository.existsActive(TEAM_ID, PROFILE_ID);
        assertCanonicalActivePredicate();

        repository.countActiveByTeamId(TEAM_ID);
        assertCanonicalActivePredicate();
    }

    @Test
    void roleUpdateTargetsOnlyCanonicalActiveMembership() {
        repository.updateRole(TEAM_ID, MEMBER_ID, TeamMemberRole.SUPPORT);

        assertCanonicalActivePredicate();
    }

    private void assertCanonicalActivePredicate() {
        assertThat(jdbcTemplate.sql)
                .contains("is_active = true")
                .contains("left_at is null");
    }

    private static class CapturingJdbcTemplate extends JdbcTemplate {

        private String sql;

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            this.sql = sql;
            return List.of();
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            this.sql = sql;
            if (requiredType == Boolean.class) {
                return requiredType.cast(Boolean.FALSE);
            }
            if (requiredType == Integer.class) {
                return requiredType.cast(0);
            }

            throw new IllegalArgumentException("Unsupported captured result type: " + requiredType.getName());
        }
    }
}
