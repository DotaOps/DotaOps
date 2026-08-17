package si.um.feri.dotaops.backend.analytics.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import si.um.feri.dotaops.backend.analytics.domain.AnalyticsFilters;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsLookupRepositoryTest {

    private static final UUID PROFILE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TOURNAMENT_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID TEAM_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID HERO_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID OTHER_PROFILE_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-05-01T00:00:00Z");
    private static final OffsetDateTime TO = OffsetDateTime.parse("2026-06-01T00:00:00Z");

    private final CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
    private final AnalyticsLookupRepository repository = new AnalyticsLookupRepository(jdbcTemplate);

    @Test
    void analyzedPlayerComparisonCandidatesUsePublicVisibilityAndBoundFilters() {
        repository.findAnalyzedPlayerComparisonCandidates(
                PROFILE_ID,
                "Aegis",
                new AnalyticsFilters(TOURNAMENT_ID, TEAM_ID, null, HERO_ID, FROM, TO, 25),
                true,
                false,
                10);

        assertThat(jdbcTemplate.sql).contains("from public.match_players mp");
        assertThat(jdbcTemplate.sql).contains("join public.matches m on m.id = coalesce(mg.match_id, mp.match_id)");
        assertThat(jdbcTemplate.sql).contains("where t.is_public = true");
        assertThat(jdbcTemplate.sql).contains("p.role = 'player'::public.dotaops_user_role");
        assertThat(jdbcTemplate.sql).contains("lower(coalesce(p.display_name, '')) like ? escape");
        assertThat(jdbcTemplate.sql).contains("lower(coalesce(p.nickname, '')) like ? escape");
        assertThat(jdbcTemplate.sql).contains("coalesce(p.opendota_account_id::text, '') like ? escape");
        assertThat(jdbcTemplate.sql).contains("analytics_games_count desc");
        assertThat(jdbcTemplate.sql).contains("p.avatar_url");
        assertThat(jdbcTemplate.sql).contains("p.opendota_account_id");
        assertThat(jdbcTemplate.sql).contains("count(*)::integer as analytics_games_count");
        assertThat(jdbcTemplate.sql).contains("m.tournament_id = ?");
        assertThat(jdbcTemplate.sql).contains("mp.team_id = ?");
        assertThat(jdbcTemplate.sql).contains("mp.hero_id = ?");
        assertThat(jdbcTemplate.sql).contains("limit ?");
        assertThat(jdbcTemplate.sql).doesNotContain("auth_user_id", "email");
        assertThat(jdbcTemplate.parameters)
                .containsExactly(
                        true,
                        PROFILE_ID,
                        "%aegis%",
                        "%aegis%",
                        "%aegis%",
                        TOURNAMENT_ID,
                        TEAM_ID,
                        HERO_ID,
                        FROM,
                        TO,
                        "aegis",
                        "aegis",
                        "aegis",
                        "aegis%",
                        "aegis%",
                        "aegis%",
                        10);
    }

    @Test
    void activeTeamPlayerComparisonCandidatesUseExactNameAndExcludeCurrentProfile() {
        repository.findActiveTeamPlayerComparisonCandidates(
                TEAM_ID,
                PROFILE_ID,
                "Aegis Ace",
                true,
                5);

        assertThat(jdbcTemplate.sql).contains("from public.team_members tm");
        assertThat(jdbcTemplate.sql).contains("where tm.team_id = ?");
        assertThat(jdbcTemplate.sql).contains("tm.left_at is null");
        assertThat(jdbcTemplate.sql).contains("p.role = 'player'::public.dotaops_user_role");
        assertThat(jdbcTemplate.sql).contains("lower(coalesce(p.display_name, '')) = ?");
        assertThat(jdbcTemplate.sql).contains("lower(coalesce(p.nickname, '')) = ?");
        assertThat(jdbcTemplate.sql).contains("coalesce(p.opendota_account_id::text, '') = ?");
        assertThat(jdbcTemplate.sql).contains("analytics_games_count desc");
        assertThat(jdbcTemplate.sql).contains("p.avatar_url");
        assertThat(jdbcTemplate.sql).contains("p.opendota_account_id");
        assertThat(jdbcTemplate.sql).contains("analytics_games_count");
        assertThat(jdbcTemplate.sql).doesNotContain("from public.match_players mp");
        assertThat(jdbcTemplate.sql).doesNotContain("auth_user_id", "email");
        assertThat(jdbcTemplate.parameters)
                .containsExactly(
                        TEAM_ID,
                        true,
                        PROFILE_ID,
                        "aegis ace",
                        "aegis ace",
                        "aegis ace",
                        "aegis ace",
                        "aegis ace",
                        "aegis ace",
                        "aegis ace%",
                        "aegis ace%",
                        "aegis ace%",
                        5);
    }

    @Test
    void currentTeamLookupRequiresCanonicalActivePlayerMembership() {
        repository.findCurrentTeams(PROFILE_ID, 10);

        assertThat(jdbcTemplate.sql)
                .contains("tm.is_active = true")
                .contains("tm.left_at is null")
                .contains("current_profile.role = 'player'::public.dotaops_user_role")
                .doesNotContain("and (\n                    t.captain_profile_id = ?");
        assertThat(jdbcTemplate.parameters).containsExactly(PROFILE_ID, PROFILE_ID, 10);
    }

    @Test
    void activeTeamPlayerLookupExcludesHistoricalAndNonPlayerMemberships() {
        repository.findActiveTeamPlayers(TEAM_ID, 10);

        assertThat(jdbcTemplate.sql)
                .contains("tm.is_active = true")
                .contains("tm.left_at is null")
                .contains("p.role = 'player'::public.dotaops_user_role");
    }

    @Test
    void activeTeamMembershipDoesNotUseCaptainIdAsMembershipFallback() {
        repository.isActiveTeamMember(TEAM_ID, PROFILE_ID);

        assertThat(jdbcTemplate.sql)
                .contains("tm.is_active = true")
                .contains("tm.left_at is null")
                .contains("member_profile.role = 'player'::public.dotaops_user_role")
                .doesNotContain("t.captain_profile_id");
        assertThat(jdbcTemplate.parameters).containsExactly(TEAM_ID, PROFILE_ID);
    }

    @Test
    void sharedMembershipRequiresThreeCanonicalActivePlayerRelations() {
        repository.teamsShareActiveMembership(PROFILE_ID, PROFILE_ID, OTHER_PROFILE_ID);

        assertThat(occurrences(jdbcTemplate.sql, "left_at is null")).isEqualTo(3);
        assertThat(occurrences(jdbcTemplate.sql, "'player'::public.dotaops_user_role")).isEqualTo(3);
        assertThat(jdbcTemplate.sql).doesNotContain("t.captain_profile_id");
        assertThat(jdbcTemplate.parameters).containsExactly(PROFILE_ID, PROFILE_ID, OTHER_PROFILE_ID);
    }

    private static int occurrences(String value, String fragment) {
        return value.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
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
