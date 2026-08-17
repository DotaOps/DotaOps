package si.um.feri.dotaops.backend.integration;

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
class RoleCapabilityPolicyIntegrationTest extends PostgresIntegrationTestSupport {

    @Test
    void organizerHelperHasNoImplicitPublicOrAnonExecutePrivilege() {
        assertThat(hasPublicExecutePrivilege("private", "is_organizer")).isFalse();
        assertThat(hasFunctionExecutePrivilege("anon", "private.is_organizer()")).isFalse();
        assertThat(hasFunctionExecutePrivilege("authenticated", "private.is_organizer()")).isTrue();
        assertThat(hasFunctionExecutePrivilege("service_role", "private.is_organizer()")).isTrue();
    }

    @Test
    void organizerRequiresOwnedOrDelegatedTournamentWhileAdminRemainsGlobal() {
        UUID organizerAAuthUserId = UUID.randomUUID();
        UUID organizerBAuthUserId = UUID.randomUUID();
        UUID playerAuthUserId = UUID.randomUUID();
        UUID adminAuthUserId = UUID.randomUUID();
        UUID organizerAProfileId = upsertProfile(organizerAAuthUserId, "organizer");
        UUID organizerBProfileId = upsertProfile(organizerBAuthUserId, "organizer");
        UUID playerProfileId = upsertProfile(playerAuthUserId, "player");
        upsertProfile(adminAuthUserId, "admin");

        UUID ownedTournamentId = insertTournament(organizerAProfileId, organizerAAuthUserId);
        UUID foreignTournamentId = insertTournament(organizerBProfileId, organizerBAuthUserId);
        UUID delegatedTournamentId = insertTournament(organizerBProfileId, organizerBAuthUserId);
        UUID playerStaffTournamentId = insertTournament(organizerBProfileId, organizerBAuthUserId);
        insertTournamentStaff(delegatedTournamentId, organizerAProfileId, "organizer");
        insertTournamentStaff(playerStaffTournamentId, playerProfileId, "owner");

        assertThat(canManageTournament(organizerAAuthUserId, ownedTournamentId)).isTrue();
        assertThat(canManageTournament(organizerAAuthUserId, delegatedTournamentId)).isTrue();
        assertThat(canManageTournament(organizerAAuthUserId, foreignTournamentId)).isFalse();
        assertThat(canManageTournament(playerAuthUserId, playerStaffTournamentId)).isFalse();
        assertThat(canManageTournament(adminAuthUserId, foreignTournamentId)).isTrue();

        assertThat(visibleTournamentCount(organizerAAuthUserId, ownedTournamentId)).isOne();
        assertThat(visibleTournamentCount(organizerAAuthUserId, delegatedTournamentId)).isOne();
        assertThat(visibleTournamentCount(organizerAAuthUserId, foreignTournamentId)).isZero();
        assertThat(visibleTournamentCount(adminAuthUserId, foreignTournamentId)).isOne();

        assertThat(updateTournamentTitle(organizerAAuthUserId, ownedTournamentId)).isOne();
        assertThat(updateTournamentTitle(organizerAAuthUserId, delegatedTournamentId)).isOne();
        assertThat(updateTournamentTitle(organizerAAuthUserId, foreignTournamentId)).isZero();
        assertThat(updateTournamentTitle(playerAuthUserId, playerStaffTournamentId)).isZero();
        assertThat(updateTournamentTitle(adminAuthUserId, foreignTournamentId)).isOne();

        assertThatThrownBy(() -> transferTournamentOwnershipDirectly(
                organizerAAuthUserId,
                ownedTournamentId,
                organizerBProfileId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> transferTournamentOwnershipDirectly(
                organizerAAuthUserId,
                delegatedTournamentId,
                organizerAProfileId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> rewriteTournamentCreatorDirectly(
                organizerAAuthUserId,
                ownedTournamentId,
                organizerBAuthUserId))
                .isInstanceOf(DataAccessException.class);
        assertThat(transferTournamentOwnershipDirectly(
                adminAuthUserId,
                foreignTournamentId,
                organizerAProfileId)).isOne();
    }

    @Test
    void organizerHasNoGlobalProfileIdentityNotificationHeroOrImportScope() {
        UUID organizerAAuthUserId = UUID.randomUUID();
        UUID organizerBAuthUserId = UUID.randomUUID();
        UUID adminAuthUserId = UUID.randomUUID();
        UUID organizerAProfileId = upsertProfile(organizerAAuthUserId, "organizer");
        UUID organizerBProfileId = upsertProfile(organizerBAuthUserId, "organizer");
        upsertProfile(adminAuthUserId, "admin");

        UUID externalAccountId = insertExternalAccount(organizerBProfileId);
        UUID notificationId = insertNotification(organizerBProfileId);
        UUID heroId = insertHero();
        UUID tournamentId = insertTournament(organizerBProfileId, organizerBAuthUserId);
        UUID matchId = insertMatch(tournamentId);
        UUID importId = insertMatchImport(matchId, organizerBProfileId);

        assertThat(visibleRowCount(organizerAAuthUserId, "profile_external_accounts", externalAccountId)).isZero();
        assertThat(visibleRowCount(adminAuthUserId, "profile_external_accounts", externalAccountId)).isOne();
        assertThat(visibleRowCount(organizerAAuthUserId, "notification_outbox", notificationId)).isZero();
        assertThat(visibleRowCount(adminAuthUserId, "notification_outbox", notificationId)).isOne();
        assertThat(visibleImportCount(organizerAAuthUserId, importId)).isZero();
        assertThat(visibleImportCount(adminAuthUserId, importId)).isOne();

        assertThat(updateOtherProfile(organizerAAuthUserId, organizerBProfileId)).isZero();
        assertThat(updateOtherProfile(organizerAAuthUserId, organizerAProfileId)).isZero();
        assertThat(changeOwnProfileRoleDirectly(
                organizerAAuthUserId,
                organizerAProfileId,
                "admin")).isZero();
        assertThat(updateOtherProfile(adminAuthUserId, organizerBProfileId)).isOne();
        assertThat(updateHero(organizerAAuthUserId, heroId)).isZero();
        assertThat(updateHero(adminAuthUserId, heroId)).isOne();
        assertThat(updateImport(organizerAAuthUserId, importId)).isZero();
        assertThat(updateImport(adminAuthUserId, importId)).isOne();
    }

    @Test
    void captainCapabilityRequiresPlayerActiveMembershipAndNonDisbandedOwnTeam() {
        UUID captainAuthUserId = UUID.randomUUID();
        UUID otherCaptainAuthUserId = UUID.randomUUID();
        UUID organizerAuthUserId = UUID.randomUUID();
        UUID captainProfileId = upsertProfile(captainAuthUserId, "player");
        UUID otherCaptainProfileId = upsertProfile(otherCaptainAuthUserId, "player");
        UUID organizerProfileId = upsertProfile(organizerAuthUserId, "organizer");

        UUID ownTeamId = insertTeam(captainProfileId, captainAuthUserId);
        UUID ownMembershipId = insertTeamMember(ownTeamId, captainProfileId, true, false);
        UUID otherTeamId = insertTeam(otherCaptainProfileId, otherCaptainAuthUserId);
        UUID otherMembershipId = insertTeamMember(otherTeamId, otherCaptainProfileId, true, false);
        UUID teamWithoutMembershipId = insertTeam(captainProfileId, captainAuthUserId);
        UUID inactiveMembershipTeamId = insertTeam(captainProfileId, captainAuthUserId);
        insertTeamMember(inactiveMembershipTeamId, captainProfileId, false, true);
        UUID inconsistentMembershipTeamId = insertTeam(captainProfileId, captainAuthUserId);
        insertTeamMember(inconsistentMembershipTeamId, captainProfileId, true, true);
        UUID disbandedTeamId = insertTeam(captainProfileId, captainAuthUserId);
        insertTeamMember(disbandedTeamId, captainProfileId, true, false);
        jdbcTemplate.update("update public.teams set disbanded_at = now() where id = ?", disbandedTeamId);
        UUID organizerCaptainTeamId = insertTeam(organizerProfileId, organizerAuthUserId);
        insertTeamMember(organizerCaptainTeamId, organizerProfileId, true, false);

        assertThat(isTeamCaptain(captainAuthUserId, ownTeamId)).isTrue();
        assertThat(isTeamCaptain(captainAuthUserId, otherTeamId)).isFalse();
        assertThat(isTeamCaptain(captainAuthUserId, teamWithoutMembershipId)).isFalse();
        assertThat(isTeamCaptain(captainAuthUserId, inactiveMembershipTeamId)).isFalse();
        assertThat(isTeamCaptain(captainAuthUserId, inconsistentMembershipTeamId)).isFalse();
        assertThat(isTeamCaptain(captainAuthUserId, disbandedTeamId)).isFalse();
        assertThat(isTeamCaptain(organizerAuthUserId, organizerCaptainTeamId)).isFalse();

        assertThat(storageOwnsTeamAsset(captainAuthUserId, ownTeamId)).isTrue();
        assertThat(storageOwnsTeamAsset(captainAuthUserId, otherTeamId)).isFalse();

        assertThat(updateTeamDescription(captainAuthUserId, ownTeamId)).isOne();
        assertThat(updateTeamDescription(captainAuthUserId, otherTeamId)).isZero();
        assertThat(updateTeamDescription(organizerAuthUserId, organizerCaptainTeamId)).isZero();

        assertThatThrownBy(() -> transferTeamDirectly(captainAuthUserId, ownTeamId, otherCaptainProfileId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> disbandTeamDirectly(captainAuthUserId, ownTeamId))
                .isInstanceOf(DataAccessException.class);

        assertThat(updateMembershipDirectly(captainAuthUserId, ownMembershipId)).isZero();
        assertThat(deleteMembershipDirectly(captainAuthUserId, ownMembershipId)).isZero();
        assertThat(updateMembershipDirectly(captainAuthUserId, otherMembershipId)).isZero();
        assertThatThrownBy(() -> insertMembershipDirectly(captainAuthUserId, ownTeamId, otherCaptainProfileId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void captainCanOnlyInsertOwnPendingRegistrationAndCannotApproveItDirectly() {
        UUID captainAuthUserId = UUID.randomUUID();
        UUID otherCaptainAuthUserId = UUID.randomUUID();
        UUID organizerAuthUserId = UUID.randomUUID();
        UUID captainProfileId = upsertProfile(captainAuthUserId, "player");
        UUID otherCaptainProfileId = upsertProfile(otherCaptainAuthUserId, "player");
        UUID organizerProfileId = upsertProfile(organizerAuthUserId, "organizer");
        UUID teamId = insertTeam(captainProfileId, captainAuthUserId);
        insertTeamMember(teamId, captainProfileId, true, false);
        UUID otherTeamId = insertTeam(otherCaptainProfileId, otherCaptainAuthUserId);
        insertTeamMember(otherTeamId, otherCaptainProfileId, true, false);
        UUID tournamentId = insertTournament(organizerProfileId, organizerAuthUserId);

        UUID pendingRegistrationId = insertPendingRegistrationDirectly(
                captainAuthUserId,
                tournamentId,
                teamId,
                captainProfileId);
        assertThat(pendingRegistrationId).isNotNull();

        assertThatThrownBy(() -> insertApprovedRegistrationDirectly(
                captainAuthUserId,
                tournamentId,
                teamId,
                captainProfileId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertPendingRegistrationDirectly(
                captainAuthUserId,
                tournamentId,
                otherTeamId,
                captainProfileId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertPendingRegistrationDirectly(
                organizerAuthUserId,
                tournamentId,
                teamId,
                organizerProfileId))
                .isInstanceOf(DataAccessException.class);

        UUID persistedRegistrationId = jdbcTemplate.queryForObject(
                """
                insert into public.tournament_registrations (
                  tournament_id,
                  team_id,
                  captain_profile_id,
                  status
                )
                values (?, ?, ?, 'pending'::public.dotaops_registration_status)
                returning id
                """,
                UUID.class,
                tournamentId,
                teamId,
                captainProfileId);

        assertThat(updateRegistrationStatusDirectly(captainAuthUserId, persistedRegistrationId)).isZero();
    }

    private boolean canManageTournament(UUID authUserId, UUID tournamentId) {
        return Boolean.TRUE.equals(asAuthenticated(authUserId, () -> jdbcTemplate.queryForObject(
                "select private.can_manage_tournament(?)",
                Boolean.class,
                tournamentId)));
    }

    private boolean hasPublicExecutePrivilege(String schemaName, String functionName) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                """
                select exists (
                  select 1
                  from pg_catalog.pg_proc p
                  join pg_catalog.pg_namespace n on n.oid = p.pronamespace
                  cross join lateral pg_catalog.aclexplode(
                    coalesce(p.proacl, pg_catalog.acldefault('f', p.proowner))
                  ) privilege
                  where n.nspname = ?
                    and p.proname = ?
                    and privilege.grantee = 0
                    and privilege.privilege_type = 'EXECUTE'
                )
                """,
                Boolean.class,
                schemaName,
                functionName));
    }

    private boolean hasFunctionExecutePrivilege(String roleName, String functionSignature) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select has_function_privilege(?, ?, 'EXECUTE')",
                Boolean.class,
                roleName,
                functionSignature));
    }

    private boolean isTeamCaptain(UUID authUserId, UUID teamId) {
        return Boolean.TRUE.equals(asAuthenticated(authUserId, () -> jdbcTemplate.queryForObject(
                "select private.is_team_captain(?)",
                Boolean.class,
                teamId)));
    }

    private boolean storageOwnsTeamAsset(UUID authUserId, UUID teamId) {
        return Boolean.TRUE.equals(asAuthenticated(authUserId, () -> jdbcTemplate.queryForObject(
                "select private.storage_team_asset_owner(?)",
                Boolean.class,
                "teams/" + teamId + "/logo.webp")));
    }

    private int visibleTournamentCount(UUID authUserId, UUID tournamentId) {
        return asAuthenticated(authUserId, () -> jdbcTemplate.queryForObject(
                "select count(*) from public.tournaments where id = ?",
                Integer.class,
                tournamentId));
    }

    private int visibleRowCount(UUID authUserId, String tableName, UUID rowId) {
        if (!tableName.equals("profile_external_accounts") && !tableName.equals("notification_outbox")) {
            throw new IllegalArgumentException("Unsupported policy test table: " + tableName);
        }

        return asAuthenticated(authUserId, () -> jdbcTemplate.queryForObject(
                "select count(*) from public." + tableName + " where id = ?",
                Integer.class,
                rowId));
    }

    private int visibleImportCount(UUID authUserId, UUID importId) {
        return asAuthenticated(authUserId, () -> jdbcTemplate.queryForObject(
                "select count(id) from public.match_imports where id = ?",
                Integer.class,
                importId));
    }

    private int updateTournamentTitle(UUID authUserId, UUID tournamentId) {
        return asAuthenticatedWithTemporaryGrant(
                authUserId,
                "grant update on public.tournaments to authenticated",
                () -> jdbcTemplate.update(
                        "update public.tournaments set title = title || ' scoped' where id = ?",
                        tournamentId));
    }

    private int updateOtherProfile(UUID authUserId, UUID profileId) {
        return asAuthenticatedWithTemporaryGrant(
                authUserId,
                "grant update on public.profiles to authenticated",
                () -> jdbcTemplate.update(
                        "update public.profiles set display_name = 'policy test' where id = ?",
                        profileId));
    }

    private int changeOwnProfileRoleDirectly(UUID authUserId, UUID profileId, String newRole) {
        return asAuthenticatedWithTemporaryGrant(
                authUserId,
                "grant update on public.profiles to authenticated",
                () -> jdbcTemplate.update(
                        "update public.profiles set role = ?::public.dotaops_user_role where id = ?",
                        newRole,
                        profileId));
    }

    private int transferTournamentOwnershipDirectly(
            UUID authUserId,
            UUID tournamentId,
            UUID newOrganizerProfileId
    ) {
        return asAuthenticatedWithTemporaryGrant(
                authUserId,
                "grant update on public.tournaments to authenticated",
                () -> jdbcTemplate.update(
                        "update public.tournaments set organizer_profile_id = ? where id = ?",
                        newOrganizerProfileId,
                        tournamentId));
    }

    private int rewriteTournamentCreatorDirectly(
            UUID authUserId,
            UUID tournamentId,
            UUID newCreatedByAuthUserId
    ) {
        return asAuthenticatedWithTemporaryGrant(
                authUserId,
                "grant update on public.tournaments to authenticated",
                () -> jdbcTemplate.update(
                        "update public.tournaments set created_by = ? where id = ?",
                        newCreatedByAuthUserId,
                        tournamentId));
    }

    private int updateHero(UUID authUserId, UUID heroId) {
        return asAuthenticatedWithTemporaryGrant(
                authUserId,
                "grant update on public.heroes to authenticated",
                () -> jdbcTemplate.update(
                        "update public.heroes set localized_name = localized_name || ' scoped' where id = ?",
                        heroId));
    }

    private int updateImport(UUID authUserId, UUID importId) {
        return asAuthenticatedWithTemporaryGrant(
                authUserId,
                "grant update on public.match_imports to authenticated",
                () -> jdbcTemplate.update(
                        "update public.match_imports set error_message = 'policy test' where id = ?",
                        importId));
    }

    private int updateTeamDescription(UUID authUserId, UUID teamId) {
        return asAuthenticatedWithTemporaryGrant(
                authUserId,
                "grant update on public.teams to authenticated",
                () -> jdbcTemplate.update(
                        "update public.teams set description = 'policy test' where id = ?",
                        teamId));
    }

    private void transferTeamDirectly(UUID authUserId, UUID teamId, UUID newCaptainProfileId) {
        asAuthenticatedWithTemporaryGrant(
                authUserId,
                "grant update on public.teams to authenticated",
                () -> jdbcTemplate.update(
                        "update public.teams set captain_profile_id = ? where id = ?",
                        newCaptainProfileId,
                        teamId));
    }

    private void disbandTeamDirectly(UUID authUserId, UUID teamId) {
        asAuthenticatedWithTemporaryGrant(
                authUserId,
                "grant update on public.teams to authenticated",
                () -> jdbcTemplate.update(
                        "update public.teams set disbanded_at = now() where id = ?",
                        teamId));
    }

    private int updateMembershipDirectly(UUID authUserId, UUID membershipId) {
        return asAuthenticatedWithTemporaryGrant(
                authUserId,
                "grant update on public.team_members to authenticated",
                () -> jdbcTemplate.update(
                        "update public.team_members set is_active = false, left_at = now() where id = ?",
                        membershipId));
    }

    private int deleteMembershipDirectly(UUID authUserId, UUID membershipId) {
        return asAuthenticatedWithTemporaryGrant(
                authUserId,
                "grant delete on public.team_members to authenticated",
                () -> jdbcTemplate.update("delete from public.team_members where id = ?", membershipId));
    }

    private void insertMembershipDirectly(UUID authUserId, UUID teamId, UUID profileId) {
        asAuthenticatedWithTemporaryGrant(
                authUserId,
                "grant insert on public.team_members to authenticated",
                () -> jdbcTemplate.update(
                        "insert into public.team_members (team_id, profile_id) values (?, ?)",
                        teamId,
                        profileId));
    }

    private UUID insertPendingRegistrationDirectly(
            UUID authUserId,
            UUID tournamentId,
            UUID teamId,
            UUID captainProfileId
    ) {
        return asAuthenticatedWithTemporaryGrant(
                authUserId,
                "grant insert on public.tournament_registrations to authenticated",
                () -> jdbcTemplate.queryForObject(
                        """
                        insert into public.tournament_registrations (
                          tournament_id,
                          team_id,
                          captain_profile_id,
                          status
                        )
                        values (?, ?, ?, 'pending'::public.dotaops_registration_status)
                        returning id
                        """,
                        UUID.class,
                        tournamentId,
                        teamId,
                        captainProfileId));
    }

    private void insertApprovedRegistrationDirectly(
            UUID authUserId,
            UUID tournamentId,
            UUID teamId,
            UUID captainProfileId
    ) {
        asAuthenticatedWithTemporaryGrant(
                authUserId,
                "grant insert on public.tournament_registrations to authenticated",
                () -> jdbcTemplate.update(
                        """
                        insert into public.tournament_registrations (
                          tournament_id,
                          team_id,
                          captain_profile_id,
                          status,
                          reviewed_by,
                          reviewed_at
                        )
                        values (?, ?, ?, 'approved'::public.dotaops_registration_status, ?, now())
                        """,
                        tournamentId,
                        teamId,
                        captainProfileId,
                        captainProfileId));
    }

    private int updateRegistrationStatusDirectly(UUID authUserId, UUID registrationId) {
        return asAuthenticatedWithTemporaryGrant(
                authUserId,
                "grant update on public.tournament_registrations to authenticated",
                () -> jdbcTemplate.update(
                        """
                        update public.tournament_registrations
                        set status = 'approved'::public.dotaops_registration_status,
                            reviewed_at = now()
                        where id = ?
                        """,
                        registrationId));
    }

    private UUID insertTournament(UUID organizerProfileId, UUID createdByAuthUserId) {
        String suffix = uniqueSuffix();
        return jdbcTemplate.queryForObject(
                """
                insert into public.tournaments (
                  slug,
                  title,
                  organizer_profile_id,
                  starts_at,
                  is_public,
                  created_by
                )
                values (?, ?, ?, now() + interval '7 days', false, ?)
                returning id
                """,
                UUID.class,
                "policy-tournament-" + suffix,
                "Policy Tournament " + suffix,
                organizerProfileId,
                createdByAuthUserId);
    }

    private void insertTournamentStaff(UUID tournamentId, UUID profileId, String role) {
        jdbcTemplate.update(
                """
                insert into public.tournament_staff (tournament_id, profile_id, staff_role)
                values (?, ?, ?::public.dotaops_tournament_staff_role)
                """,
                tournamentId,
                profileId,
                role);
    }

    private UUID insertTeam(UUID captainProfileId, UUID createdByAuthUserId) {
        String suffix = uniqueSuffix();
        return jdbcTemplate.queryForObject(
                """
                insert into public.teams (name, slug, captain_profile_id, created_by)
                values (?, ?, ?, ?)
                returning id
                """,
                UUID.class,
                "Policy Team " + suffix,
                "policy-team-" + suffix,
                captainProfileId,
                createdByAuthUserId);
    }

    private UUID insertTeamMember(UUID teamId, UUID profileId, boolean active, boolean hasLeftAt) {
        return jdbcTemplate.queryForObject(
                """
                insert into public.team_members (team_id, profile_id, is_active, left_at)
                values (?, ?, ?, case when ? then now() else null end)
                returning id
                """,
                UUID.class,
                teamId,
                profileId,
                active,
                hasLeftAt);
    }

    private UUID insertExternalAccount(UUID profileId) {
        return jdbcTemplate.queryForObject(
                """
                insert into public.profile_external_accounts (
                  profile_id,
                  provider,
                  provider_account_id,
                  display_name
                )
                values (?, 'discord'::public.dotaops_external_account_provider, ?, 'Private identity')
                returning id
                """,
                UUID.class,
                profileId,
                "discord-" + uniqueSuffix());
    }

    private UUID insertNotification(UUID profileId) {
        return jdbcTemplate.queryForObject(
                """
                insert into public.notification_outbox (
                  recipient_profile_id,
                  channel,
                  type,
                  title,
                  message
                )
                values (
                  ?,
                  'in_app'::public.dotaops_notification_channel,
                  'system'::public.dotaops_notification_type,
                  'Private notification',
                  'Private message'
                )
                returning id
                """,
                UUID.class,
                profileId);
    }

    private UUID insertHero() {
        int dotaHeroId = Math.floorMod(UUID.randomUUID().hashCode(), 1_000_000_000) + 10_000;
        String suffix = uniqueSuffix();
        return jdbcTemplate.queryForObject(
                """
                insert into public.heroes (dota_hero_id, name, localized_name)
                values (?, ?, ?)
                returning id
                """,
                UUID.class,
                dotaHeroId,
                "npc_dota_hero_policy_" + suffix,
                "Policy Hero " + suffix);
    }

    private UUID insertMatch(UUID tournamentId) {
        return jdbcTemplate.queryForObject(
                """
                insert into public.matches (tournament_id, round_name)
                values (?, 'Policy round')
                returning id
                """,
                UUID.class,
                tournamentId);
    }

    private UUID insertMatchImport(UUID matchId, UUID requestedByProfileId) {
        return jdbcTemplate.queryForObject(
                """
                insert into public.match_imports (match_id, dota_match_id, requested_by)
                values (?, ?, ?)
                returning id
                """,
                UUID.class,
                matchId,
                UUID.randomUUID().toString(),
                requestedByProfileId);
    }
}
