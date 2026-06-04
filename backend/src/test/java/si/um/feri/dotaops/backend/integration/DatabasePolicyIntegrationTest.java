package si.um.feri.dotaops.backend.integration;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("integration")
@EnabledIfEnvironmentVariable(named = "SUPABASE_DB_URL", matches = ".+")
class DatabasePolicyIntegrationTest extends PostgresIntegrationTestSupport {

    @Test
    void authUserInsertCreatesDefaultPlayerProfile() {
        UUID authUserId = UUID.randomUUID();
        seedAuthUser(authUserId);

        Map<String, Object> profile = jdbcTemplate.queryForMap(
                """
                select auth_user_id, role::text as role
                from public.profiles
                where auth_user_id = ?
                """,
                authUserId);

        assertThat(profile.get("auth_user_id")).isEqualTo(authUserId);
        assertThat(profile.get("role")).isEqualTo("player");
    }

    @Test
    void authUserProfileTriggerDoesNotCreateDuplicateRows() {
        UUID authUserId = UUID.randomUUID();
        seedAuthUser(authUserId);
        seedAuthUser(authUserId);

        Integer profileCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from public.profiles
                where auth_user_id = ?
                """,
                Integer.class,
                authUserId);

        assertThat(profileCount).isOne();
    }

    @Test
    void serviceRoleProfileHelperAllowsOrganizerButNotGlobalCaptainFromMetadata() {
        UUID organizerAuthUserId = UUID.randomUUID();
        UUID captainAuthUserId = UUID.randomUUID();
        seedAuthUser(organizerAuthUserId);
        seedAuthUser(captainAuthUserId);

        jdbcTemplate.update("delete from public.profiles where auth_user_id in (?, ?)",
                organizerAuthUserId,
                captainAuthUserId);

        asServiceRole(() -> {
            jdbcTemplate.queryForObject(
                    """
                    select private.ensure_profile_for_auth_user(
                      ?,
                      ?,
                      ?::jsonb,
                      '{}'::jsonb
                    )
                    """,
                    UUID.class,
                    organizerAuthUserId,
                    "organizer@example.test",
                    "{\"desired_role\":\"organizer\",\"nickname\":\"metadata_organizer\"}");
            jdbcTemplate.queryForObject(
                    """
                    select private.ensure_profile_for_auth_user(
                      ?,
                      ?,
                      ?::jsonb,
                      '{}'::jsonb
                    )
                    """,
                    UUID.class,
                    captainAuthUserId,
                    "captain@example.test",
                    "{\"desired_role\":\"captain\",\"nickname\":\"metadata_captain\"}");

            return null;
        });

        Map<String, Object> organizerProfile = jdbcTemplate.queryForMap(
                "select role::text as role from public.profiles where auth_user_id = ?",
                organizerAuthUserId);
        Map<String, Object> captainProfile = jdbcTemplate.queryForMap(
                "select role::text as role from public.profiles where auth_user_id = ?",
                captainAuthUserId);

        assertThat(organizerProfile.get("role")).isEqualTo("organizer");
        assertThat(captainProfile.get("role")).isEqualTo("player");
    }

    @Test
    void authenticatedUserCannotCreateProfileForAnotherUser() {
        UUID authUserId = UUID.randomUUID();
        UUID otherAuthUserId = UUID.randomUUID();
        seedAuthUser(authUserId);
        seedAuthUser(otherAuthUserId);

        assertThatThrownBy(() -> asAuthenticated(authUserId, () -> {
            jdbcTemplate.update(
                    """
                    insert into public.profiles (auth_user_id, nickname)
                    values (?, ?)
                    """,
                    otherAuthUserId,
                    "other_" + uniqueSuffix());
            return null;
        })).isInstanceOf(DataAccessException.class);
    }

    @Test
    void profileConstraintRejectsPersistedVisitorRole() {
        UUID authUserId = UUID.randomUUID();
        seedAuthUser(authUserId);

        assertThatThrownBy(() -> upsertProfile(authUserId, "visitor"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void authenticatedClientCannotMutateSteamExternalAccountsDirectly() {
        UUID authUserId = UUID.randomUUID();
        UUID profileId = upsertProfile(authUserId, "player");

        assertThatThrownBy(() -> asAuthenticated(authUserId, () -> {
            jdbcTemplate.update(
                    """
                    insert into public.profile_external_accounts (
                        profile_id,
                        provider,
                        provider_account_id,
                        is_primary
                    )
                    values (?, 'steam'::public.dotaops_external_account_provider, ?, true)
                    """,
                    profileId,
                    uniqueSteamId64());
            return null;
        })).isInstanceOf(DataAccessException.class);
    }

    @Test
    void onlyAdminCanReadAuditLogThroughAuthenticatedRole() {
        UUID playerAuthUserId = UUID.randomUUID();
        UUID adminAuthUserId = UUID.randomUUID();
        upsertProfile(playerAuthUserId, "player");
        upsertProfile(adminAuthUserId, "admin");
        UUID auditLogId = asServiceRole(() -> jdbcTemplate.queryForObject(
                """
                insert into public.audit_log (table_name, record_id, action, new_row)
                values ('public.teams', ?, 'insert'::public.dotaops_audit_action, '{}'::jsonb)
                returning id
                """,
                UUID.class,
                UUID.randomUUID()));

        Integer playerVisibleRows = asAuthenticated(playerAuthUserId, () -> jdbcTemplate.queryForObject(
                "select count(*) from public.audit_log where id = ?",
                Integer.class,
                auditLogId));
        Integer adminVisibleRows = asAuthenticated(adminAuthUserId, () -> jdbcTemplate.queryForObject(
                "select count(*) from public.audit_log where id = ?",
                Integer.class,
                auditLogId));

        assertThat(playerVisibleRows).isZero();
        assertThat(adminVisibleRows).isOne();
        assertThatThrownBy(() -> asRole("anon", null, () -> jdbcTemplate.queryForObject(
                "select count(*) from public.audit_log where id = ?",
                Integer.class,
                auditLogId)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void storageOwnershipHelpersRestrictAvatarAndTeamAssetPaths() {
        UUID captainAuthUserId = UUID.randomUUID();
        UUID otherAuthUserId = UUID.randomUUID();
        UUID captainProfileId = upsertProfile(captainAuthUserId, "player");
        UUID otherProfileId = upsertProfile(otherAuthUserId, "player");
        UUID teamId = insertTeam(captainProfileId);

        Boolean ownAvatar = asAuthenticated(captainAuthUserId, () -> jdbcTemplate.queryForObject(
                "select private.storage_profile_avatar_owner(?)",
                Boolean.class,
                "profiles/" + captainProfileId + "/avatar.png"));
        Boolean otherAvatar = asAuthenticated(captainAuthUserId, () -> jdbcTemplate.queryForObject(
                "select private.storage_profile_avatar_owner(?)",
                Boolean.class,
                "profiles/" + otherProfileId + "/avatar.png"));
        Boolean captainTeamAsset = asAuthenticated(captainAuthUserId, () -> jdbcTemplate.queryForObject(
                "select private.storage_team_asset_owner(?)",
                Boolean.class,
                "teams/" + teamId + "/logo.webp"));
        Boolean nonCaptainTeamAsset = asAuthenticated(otherAuthUserId, () -> jdbcTemplate.queryForObject(
                "select private.storage_team_asset_owner(?)",
                Boolean.class,
                "teams/" + teamId + "/banner.jpg"));

        assertThat(ownAvatar).isTrue();
        assertThat(otherAvatar).isFalse();
        assertThat(captainTeamAsset).isTrue();
        assertThat(nonCaptainTeamAsset).isFalse();
    }

    @Test
    void serviceRoleSteamHelperLinksPrimarySteamToAutoCreatedAuthProfile() {
        UUID authUserId = UUID.randomUUID();
        seedAuthUser(authUserId);
        UUID existingProfileId = jdbcTemplate.queryForObject(
                "select id from public.profiles where auth_user_id = ?",
                UUID.class,
                authUserId);
        String steamId64 = uniqueSteamId64();
        String suffix = uniqueSuffix();

        Map<String, Object> result = asServiceRole(() -> jdbcTemplate.queryForMap(
                """
                select
                  out_profile_id,
                  out_external_account_id,
                  out_is_new_profile,
                  out_is_new_external_account
                from private.upsert_steam_profile(
                  ?,
                  ?,
                  ?,
                  ?,
                  ?,
                  ?,
                  ?::jsonb,
                  true
                )
                """,
                steamId64,
                authUserId,
                "steam_" + suffix,
                "Steam " + suffix,
                "https://cdn.example.test/avatar/" + suffix + ".png",
                "https://steamcommunity.com/profiles/" + steamId64 + "/",
                "{\"source\":\"integration-test\"}"));

        UUID profileId = (UUID) result.get("out_profile_id");
        UUID externalAccountId = (UUID) result.get("out_external_account_id");

        assertThat(result.get("out_is_new_profile")).isEqualTo(Boolean.FALSE);
        assertThat(result.get("out_is_new_external_account")).isEqualTo(Boolean.TRUE);
        assertThat(profileId).isEqualTo(existingProfileId);
        assertThat(externalAccountId).isNotNull();

        Map<String, Object> persisted = jdbcTemplate.queryForMap(
                """
                select
                  p.steam_id,
                  pea.provider_account_id,
                  pea.is_primary,
                  pea.is_login_identity
                from public.profiles p
                join public.profile_external_accounts pea
                  on pea.profile_id = p.id
                where p.id = ?
                  and pea.id = ?
                """,
                profileId,
                externalAccountId);

        assertThat(persisted.get("steam_id")).isEqualTo(steamId64);
        assertThat(persisted.get("provider_account_id")).isEqualTo(steamId64);
        assertThat(persisted.get("is_primary")).isEqualTo(Boolean.TRUE);
        assertThat(persisted.get("is_login_identity")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void serviceRoleSteamHelperCreatesPrimarySteamOnlyProfile() {
        String steamId64 = uniqueSteamId64();
        String suffix = uniqueSuffix();

        Map<String, Object> result = asServiceRole(() -> jdbcTemplate.queryForMap(
                """
                select
                  out_profile_id,
                  out_external_account_id,
                  out_is_new_profile,
                  out_is_new_external_account
                from private.upsert_steam_profile(
                  ?,
                  null,
                  ?,
                  ?,
                  ?,
                  ?,
                  ?::jsonb,
                  true
                )
                """,
                steamId64,
                "steam_only_" + suffix,
                "Steam Only " + suffix,
                "https://cdn.example.test/avatar/" + suffix + ".png",
                "https://steamcommunity.com/profiles/" + steamId64 + "/",
                "{\"source\":\"integration-test\"}"));

        UUID profileId = (UUID) result.get("out_profile_id");
        UUID externalAccountId = (UUID) result.get("out_external_account_id");

        assertThat(result.get("out_is_new_profile")).isEqualTo(Boolean.TRUE);
        assertThat(result.get("out_is_new_external_account")).isEqualTo(Boolean.TRUE);
        assertThat(profileId).isNotNull();
        assertThat(externalAccountId).isNotNull();

        Map<String, Object> persisted = jdbcTemplate.queryForMap(
                """
                select
                  p.auth_user_id,
                  p.steam_id,
                  pea.provider_account_id,
                  pea.is_primary,
                  pea.is_login_identity
                from public.profiles p
                join public.profile_external_accounts pea
                  on pea.profile_id = p.id
                where p.id = ?
                  and pea.id = ?
                """,
                profileId,
                externalAccountId);

        assertThat(persisted.get("auth_user_id")).isNull();
        assertThat(persisted.get("steam_id")).isEqualTo(steamId64);
        assertThat(persisted.get("provider_account_id")).isEqualTo(steamId64);
        assertThat(persisted.get("is_primary")).isEqualTo(Boolean.TRUE);
        assertThat(persisted.get("is_login_identity")).isEqualTo(Boolean.TRUE);
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
                "Storage Policy Team " + suffix,
                "storage-policy-team-" + suffix,
                captainProfileId);
    }
}
