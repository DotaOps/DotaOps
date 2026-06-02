package si.um.feri.dotaops.backend.integration;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
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
                      ('match_players'),
                      ('notification_outbox'),
                      ('audit_log')
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
                      ('match_games_winner_side_allowed'),
                      ('match_players_team_side_allowed'),
                      ('match_players_dota_account_id_range'),
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
                      ('match_games_dota_match_id_idx'),
                      ('match_games_import_status_idx'),
                      ('match_players_profile_id_idx'),
                      ('match_players_team_id_idx'),
                      ('match_players_hero_id_idx'),
                      ('match_players_dota_hero_id_idx'),
                      ('match_players_dota_account_id_idx'),
                      ('match_players_team_side_idx'),
                      ('match_players_match_game_id_idx'),
                      ('matches_status_idx'),
                      ('steam_login_states_expires_idx'),
                      ('matches_tournament_stage_idx'),
                      ('audit_log_created_at_idx'),
                      ('notification_outbox_recipient_profile_id_idx'),
                      ('notification_outbox_status_idx'),
                      ('notification_outbox_type_idx'),
                      ('notification_outbox_status_next_attempt_at_idx')
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
        Integer privateAnalyticsRefresh = jdbcTemplate.queryForObject(
                """
                select count(*)
                from pg_proc p
                join pg_namespace n on n.oid = p.pronamespace
                where n.nspname = 'private'
                  and p.proname = 'refresh_dotaops_analytics'
                """,
                Integer.class);
        List<String> missingAnalyticsViews = jdbcTemplate.queryForList(
                """
                select expected.viewname
                from (
                    values
                      ('v_player_metrics'),
                      ('v_team_metrics'),
                      ('v_hero_metrics'),
                      ('v_tournament_metrics')
                ) as expected(viewname)
                left join pg_views v
                  on v.schemaname = 'public'
                 and v.viewname = expected.viewname
                where v.viewname is null
                order by expected.viewname
                """,
                String.class);

        assertThat(missingConstraints).isEmpty();
        assertThat(missingIndexes).isEmpty();
        assertThat(privateSteamHelpers).isEqualTo(4);
        assertThat(privateAnalyticsRefresh).isOne();
        assertThat(missingAnalyticsViews).isEmpty();
        assertThat(profileCreationTrigger).isOne();
    }

    @Test
    void operationalAuditTriggersCoverRequiredTables() {
        List<String> missingAuditTriggers = jdbcTemplate.queryForList(
                """
                select expected.trigger_name
                from (
                    values
                      ('audit_teams', 'teams'),
                      ('audit_tournaments', 'tournaments'),
                      ('audit_tournament_registrations', 'tournament_registrations'),
                      ('audit_matches', 'matches'),
                      ('audit_match_games', 'match_games'),
                      ('audit_match_imports', 'match_imports'),
                      ('audit_match_players', 'match_players')
                ) as expected(trigger_name, table_name)
                left join pg_trigger trigger
                  on trigger.tgname = expected.trigger_name
                 and not trigger.tgisinternal
                left join pg_class target_table
                  on target_table.oid = trigger.tgrelid
                 and target_table.relname = expected.table_name
                left join pg_namespace target_schema
                  on target_schema.oid = target_table.relnamespace
                 and target_schema.nspname = 'public'
                where target_schema.oid is null
                order by expected.trigger_name
                """,
                String.class);

        assertThat(missingAuditTriggers).isEmpty();
    }

    @Test
    void captainRosterBackfillReactivatesLatestHistoricalMembershipOrCreatesMissingMembership() {
        UUID historicalCaptainProfileId = upsertProfile(UUID.randomUUID(), "player");
        UUID missingCaptainProfileId = upsertProfile(UUID.randomUUID(), "player");
        UUID historicalTeamId = insertTeam(historicalCaptainProfileId);
        UUID missingTeamId = insertTeam(missingCaptainProfileId);

        insertInactiveTeamMember(historicalTeamId, historicalCaptainProfileId, "3 days", "2 days");
        UUID latestHistoricalMemberId = insertInactiveTeamMember(
                historicalTeamId,
                historicalCaptainProfileId,
                "2 days",
                "1 day");

        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V30__backfill_team_captain_roster_membership.sql"))
                .execute(jdbcTemplate.getDataSource());

        List<UUID> reactivatedMemberIds = jdbcTemplate.queryForList(
                """
                select id
                from public.team_members
                where team_id = ?
                  and profile_id = ?
                  and is_active
                """,
                UUID.class,
                historicalTeamId,
                historicalCaptainProfileId);
        Boolean reactivatedMemberClearedLeftAt = jdbcTemplate.queryForObject(
                "select left_at is null from public.team_members where id = ?",
                Boolean.class,
                latestHistoricalMemberId);
        Integer insertedActiveMemberships = jdbcTemplate.queryForObject(
                """
                select count(*)
                from public.team_members
                where team_id = ?
                  and profile_id = ?
                  and is_active
                """,
                Integer.class,
                missingTeamId,
                missingCaptainProfileId);

        assertThat(reactivatedMemberIds).containsExactly(latestHistoricalMemberId);
        assertThat(reactivatedMemberClearedLeftAt).isTrue();
        assertThat(insertedActiveMemberships).isOne();
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

    private UUID insertTeam(UUID captainProfileId) {
        String suffix = uniqueSuffix();

        return jdbcTemplate.queryForObject(
                """
                insert into public.teams (name, slug, captain_profile_id)
                values (?, ?, ?)
                returning id
                """,
                UUID.class,
                "Migration Team " + suffix,
                "migration-team-" + suffix,
                captainProfileId);
    }

    private UUID insertInactiveTeamMember(
            UUID teamId,
            UUID profileId,
            String joinedAgo,
            String leftAgo
    ) {
        return jdbcTemplate.queryForObject(
                """
                insert into public.team_members (
                  team_id,
                  profile_id,
                  member_role,
                  is_active,
                  joined_at,
                  left_at,
                  updated_at
                )
                values (
                  ?,
                  ?,
                  'support'::public.dotaops_team_member_role,
                  false,
                  now() - cast(? as interval),
                  now() - cast(? as interval),
                  now() - cast(? as interval)
                )
                returning id
                """,
                UUID.class,
                teamId,
                profileId,
                joinedAgo,
                leftAgo,
                leftAgo);
    }
}
