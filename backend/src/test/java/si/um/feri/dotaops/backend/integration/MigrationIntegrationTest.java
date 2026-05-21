package si.um.feri.dotaops.backend.integration;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
@EnabledIfEnvironmentVariable(named = "SUPABASE_DB_URL", matches = ".+")
class MigrationIntegrationTest extends PostgresIntegrationTestSupport {

    private static final Pattern VERSIONED_MIGRATION_FILENAME = Pattern.compile("^V(.+)__.+\\.sql$");

    @Autowired
    private ResourcePatternResolver resourcePatternResolver;

    @Test
    void flywayAppliesAllCurrentMigrations() throws IOException {
        List<String> expectedVersions = currentMigrationVersions();
        List<String> appliedVersions = jdbcTemplate.queryForList(
                """
                select version
                from public.flyway_schema_history
                where success
                  and version is not null
                order by installed_rank
                """,
                String.class);
        Integer failedMigrations = jdbcTemplate.queryForObject(
                "select count(*) from public.flyway_schema_history where not success",
                Integer.class);

        assertThat(failedMigrations).isZero();
        assertThat(appliedVersions).containsExactlyElementsOf(expectedVersions);
    }

    @Test
    void coreTablesHaveRlsEnabledAfterMigration() {
        List<String> tablesWithoutRls = jdbcTemplate.queryForList(
                """
                select expected.relname
                from (
                    values
                      ('profiles'),
                      ('profile_external_accounts'),
                      ('teams'),
                      ('team_members'),
                      ('tournaments'),
                      ('tournament_registrations'),
                      ('matches'),
                      ('match_advancement_audit_logs'),
                      ('match_games'),
                      ('match_import_events'),
                      ('match_players')
                ) as expected(relname)
                left join pg_class c
                  on c.relname = expected.relname
                left join pg_namespace n
                  on n.oid = c.relnamespace
                 and n.nspname = 'public'
                where n.oid is null
                   or not c.relrowsecurity
                order by expected.relname
                """,
                String.class);

        assertThat(tablesWithoutRls).isEmpty();
    }

    @Test
    void securityHelpersAndKeyConstraintsExist() {
        List<String> missingConstraints = jdbcTemplate.queryForList(
                """
                select expected.conname
                from (
                    values
                      ('profiles_steam_id_format'),
                      ('profiles_role_no_global_captain'),
                      ('profiles_opendota_account_id_range'),
                      ('profile_external_accounts_steam_id64_format'),
                      ('matches_scores_fit_series'),
                      ('matches_cancellation_reason_length'),
                      ('matches_cancelled_at_status'),
                      ('match_advancement_audit_source_type'),
                      ('match_import_events_error_code_allowed'),
                      ('tournaments_registration_before_start')
                ) as expected(conname)
                left join pg_constraint c
                  on c.conname = expected.conname
                where c.oid is null
                order by expected.conname
                """,
                String.class);
        List<String> missingIndexes = jdbcTemplate.queryForList(
                """
                select expected.indexname
                from (
                    values
                      ('profile_external_accounts_one_primary_idx'),
                      ('profiles_nickname_ci_unique_idx'),
                      ('profiles_opendota_account_id_unique_idx'),
                      ('heroes_dota_hero_id_idx'),
                      ('match_slots_team_idx'),
                      ('match_slots_source_match_type_idx'),
                      ('match_slots_locked_idx'),
                      ('match_advancement_audit_source_match_idx'),
                      ('match_import_events_error_code_idx'),
                      ('match_players_dota_hero_id_idx'),
                      ('matches_status_idx'),
                      ('steam_login_states_expires_idx'),
                      ('matches_tournament_stage_idx')
                ) as expected(indexname)
                left join pg_indexes i
                  on i.schemaname in ('public', 'private')
                 and i.indexname = expected.indexname
                where i.indexname is null
                order by expected.indexname
                """,
                String.class);
        Integer privateSteamHelpers = jdbcTemplate.queryForObject(
                """
                select count(*)
                from pg_proc p
                join pg_namespace n on n.oid = p.pronamespace
                where n.nspname = 'private'
                  and p.proname in (
                    'upsert_steam_profile',
                    'link_steam_account_to_profile',
                    'create_steam_login_state',
                    'consume_steam_login_state'
                  )
                """,
                Integer.class);
        Integer profileCreationTrigger = jdbcTemplate.queryForObject(
                """
                select count(*)
                from pg_trigger t
                join pg_class c on c.oid = t.tgrelid
                join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = 'auth'
                  and c.relname = 'users'
                  and t.tgname = 'dotaops_create_profile_on_auth_user'
                  and not t.tgisinternal
                """,
                Integer.class);

        assertThat(missingConstraints).isEmpty();
        assertThat(missingIndexes).isEmpty();
        assertThat(privateSteamHelpers).isEqualTo(4);
        assertThat(profileCreationTrigger).isOne();
    }

    private List<String> currentMigrationVersions() throws IOException {
        Resource[] resources = resourcePatternResolver.getResources("classpath*:db/migration/V*.sql");

        assertThat(resources).isNotEmpty();
        return Arrays.stream(resources)
                .map(Resource::getFilename)
                .map(MigrationIntegrationTest::extractMigrationVersion)
                .sorted((left, right) -> MigrationVersion.fromVersion(left)
                        .compareTo(MigrationVersion.fromVersion(right)))
                .toList();
    }

    private static String extractMigrationVersion(String filename) {
        Matcher matcher = VERSIONED_MIGRATION_FILENAME.matcher(filename);

        assertThat(matcher.matches())
                .as("Versioned Flyway migration filename should match V<version>__<description>.sql: %s", filename)
                .isTrue();

        return matcher.group(1).replace('_', '.');
    }
}
