package si.um.feri.dotaops.backend.integration;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedActor;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.common.security.DatabaseActorContext;
import si.um.feri.dotaops.backend.tournament.domain.TournamentFormat;
import si.um.feri.dotaops.backend.tournament.domain.TournamentSettings;
import si.um.feri.dotaops.backend.tournament.mapper.TournamentSettingsMapper;
import si.um.feri.dotaops.backend.tournament.repository.CreateTournamentCommand;
import si.um.feri.dotaops.backend.tournament.repository.TournamentRepository;
import si.um.feri.dotaops.backend.tournament.repository.UpdateTournamentCommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("integration")
@EnabledIfEnvironmentVariable(named = "SUPABASE_DB_URL", matches = ".+")
class TournamentDatabaseFlowIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    private TournamentRepository tournamentRepository;

    @Autowired
    private TournamentSettingsMapper tournamentSettingsMapper;

    @Autowired
    private DatabaseActorContext databaseActorContext;

    @Test
    void captainOrganizerAndResultFlowRespectsDatabasePolicies() {
        UUID captainAuthUserId = UUID.randomUUID();
        UUID organizerAuthUserId = UUID.randomUUID();
        UUID playerAuthUserId = UUID.randomUUID();
        UUID captainProfileId = upsertProfile(captainAuthUserId, "player");
        UUID organizerProfileId = upsertProfile(organizerAuthUserId, "organizer");
        UUID playerProfileId = upsertProfile(playerAuthUserId, "player");
        String suffix = uniqueSuffix();

        UUID teamId = asAuthenticated(captainAuthUserId, () -> insertTeam(
                "Captain Team " + suffix,
                "captain-team-" + suffix,
                captainProfileId,
                captainAuthUserId));
        UUID opponentTeamId = asAuthenticated(playerAuthUserId, () -> insertTeam(
                "Opponent Team " + suffix,
                "opponent-team-" + suffix,
                playerProfileId,
                playerAuthUserId));
        assertThatThrownBy(() -> asAuthenticated(organizerAuthUserId, () -> insertTeam(
                "Organizer Team " + suffix,
                "organizer-team-" + suffix,
                organizerProfileId,
                organizerAuthUserId))).isInstanceOf(DataAccessException.class);
        assertThat(asAuthenticated(organizerAuthUserId, () -> jdbcTemplate.update(
                "update public.teams set region = 'NA' where id = ?",
                teamId))).isZero();
        UUID tournamentId = asAuthenticated(organizerAuthUserId, () -> insertTournament(
                "integration-cup-" + suffix,
                "Integration Cup " + suffix,
                organizerProfileId,
                organizerAuthUserId));

        UUID registrationId = asAuthenticated(captainAuthUserId, () -> jdbcTemplate.queryForObject(
                """
                insert into public.tournament_registrations (
                    tournament_id,
                    team_id,
                    captain_profile_id,
                    message
                )
                values (?, ?, ?, ?)
                returning id
                """,
                UUID.class,
                tournamentId,
                teamId,
                captainProfileId,
                "Ready for CI integration flow"));

        UUID matchId = asAuthenticated(organizerAuthUserId, () -> jdbcTemplate.queryForObject(
                """
                insert into public.matches (
                    tournament_id,
                    round_name,
                    round_number,
                    team_a_id,
                    team_b_id,
                    status,
                    best_of,
                    score_a,
                    score_b,
                    winner_team_id
                )
                values (?, 'Final', 1, ?, ?, 'finished'::public.dotaops_match_status, 1, 1, 0, ?)
                returning id
                """,
                UUID.class,
                tournamentId,
                teamId,
                opponentTeamId,
                teamId));

        UUID matchGameId = asAuthenticated(organizerAuthUserId, () -> jdbcTemplate.queryForObject(
                """
                insert into public.match_games (
                    match_id,
                    game_number,
                    status,
                    import_status,
                    radiant_team_id,
                    dire_team_id,
                    winner_team_id,
                    duration_seconds,
                    started_at,
                    finished_at
                )
                values (
                    ?,
                    1,
                    'finished'::public.dotaops_match_status,
                    'ready'::public.dotaops_import_status,
                    ?,
                    ?,
                    ?,
                    2400,
                    now() - interval '45 minutes',
                    now()
                )
                returning id
                """,
                UUID.class,
                matchId,
                teamId,
                opponentTeamId,
                teamId));

        assertThat(teamId).isNotNull();
        assertThat(tournamentId).isNotNull();
        assertThat(registrationId).isNotNull();
        assertThat(matchId).isNotNull();
        assertThat(matchGameId).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "select status::text from public.tournament_registrations where id = ?",
                String.class,
                registrationId)).isEqualTo("pending");

        assertThatThrownBy(() -> asAuthenticated(playerAuthUserId, () -> insertTournament(
                "player-owned-cup-" + suffix,
                "Player Owned Cup " + suffix,
                null,
                playerAuthUserId))).isInstanceOf(DataAccessException.class);
    }

    @Test
    void tournamentRepositoryPersistsSettingsPublishesAndWritesAuditActorContext() {
        UUID organizerAuthUserId = UUID.randomUUID();
        UUID organizerProfileId = upsertProfile(organizerAuthUserId, "organizer");
        String suffix = uniqueSuffix();

        UUID tournamentId = asAuthenticated(organizerAuthUserId, () -> {
            databaseActorContext.apply(new AuthenticatedActor(
                    organizerAuthUserId,
                    organizerProfileId,
                    organizerAuthUserId + "@integration.test",
                    null,
                    ProfileRole.ORGANIZER));

            TournamentSettings initialSettings = new TournamentSettings(
                    8,
                    2,
                    5,
                    1,
                    TournamentFormat.SINGLE_ELIMINATION,
                    false,
                    true);

            var created = tournamentRepository.create(new CreateTournamentCommand(
                    "settings-audit-cup-" + suffix,
                    "Settings Audit Cup " + suffix,
                    TournamentFormat.SINGLE_ELIMINATION,
                    organizerProfileId,
                    "Integration settings and audit flow",
                    "Default rules",
                    "TBD",
                    initialSettings.maxTeams(),
                    java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusDays(14),
                    null,
                    java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusDays(1),
                    java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusDays(7),
                    organizerAuthUserId,
                    "UTC",
                    null,
                    null,
                    tournamentSettingsMapper.toJson(initialSettings)));

            TournamentSettings updatedSettings = new TournamentSettings(
                    16,
                    4,
                    5,
                    3,
                    TournamentFormat.SINGLE_ELIMINATION,
                    false,
                    true);

            var updated = tournamentRepository.update(
                            created.id(),
                            new UpdateTournamentCommand(
                                    false,
                                    null,
                                    false,
                                    null,
                                    false,
                                    null,
                                    false,
                                    null,
                                    false,
                                    null,
                                    false,
                                    null,
                                    true,
                                    updatedSettings.maxTeams(),
                                    false,
                                    null,
                                    false,
                                    null,
                                    false,
                                    null,
                                    false,
                                    null,
                                    false,
                                    null,
                                    false,
                                    null,
                                    false,
                                    null,
                                    true,
                                    tournamentSettingsMapper.toJson(updatedSettings)))
                    .orElseThrow();

            assertThat(updated.settings().maxTeams()).isEqualTo(16);
            assertThat(updated.settings().bestOf()).isEqualTo(3);

            var published = tournamentRepository.publish(created.id()).orElseThrow();
            assertThat(published.status().databaseValue()).isEqualTo("published");
            assertThat(published.publishedAt()).isNotNull();

            return created.id();
        });

        var auditRow = jdbcTemplate.queryForMap(
                """
                select
                  actor_auth_user_id,
                  actor_profile_id,
                  action::text as action
                from public.audit_log
                where table_name = 'public.tournaments'
                  and record_id = ?
                  and action = 'update'::public.dotaops_audit_action
                order by created_at desc
                limit 1
                """,
                tournamentId);

        assertThat(auditRow.get("actor_auth_user_id")).isEqualTo(organizerAuthUserId);
        assertThat(auditRow.get("actor_profile_id")).isEqualTo(organizerProfileId);
        assertThat(auditRow.get("action")).isEqualTo("update");
    }

    private UUID insertTeam(String name, String slug, UUID captainProfileId, UUID createdBy) {
        return jdbcTemplate.queryForObject(
                """
                insert into public.teams (
                    name,
                    slug,
                    captain_profile_id,
                    created_by
                )
                values (?, ?, ?, ?)
                returning id
                """,
                UUID.class,
                name,
                slug,
                captainProfileId,
                createdBy);
    }

    private UUID insertTournament(String slug, String title, UUID organizerProfileId, UUID createdBy) {
        return jdbcTemplate.queryForObject(
                """
                insert into public.tournaments (
                    slug,
                    title,
                    organizer_profile_id,
                    starts_at,
                    registration_closes_at,
                    created_by
                )
                values (?, ?, ?, now() + interval '14 days', now() + interval '7 days', ?)
                returning id
                """,
                UUID.class,
                slug,
                title,
                organizerProfileId,
                createdBy);
    }
}
