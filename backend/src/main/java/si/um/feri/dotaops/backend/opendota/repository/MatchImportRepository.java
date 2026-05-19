package si.um.feri.dotaops.backend.opendota.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import si.um.feri.dotaops.backend.opendota.domain.MatchImport;
import si.um.feri.dotaops.backend.opendota.domain.MatchPlayerImport;
import si.um.feri.dotaops.backend.opendota.domain.MatchImportStatus;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaErrorCode;

@Repository
public class MatchImportRepository {

    private final JdbcTemplate jdbcTemplate;

    public MatchImportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<MatchImport> findByDotaMatchId(String dotaMatchId) {
        return jdbcTemplate.query(
                        selectSql() + """
                        where dota_match_id = ?
                        limit 1
                        """,
                        this::mapMatchImport,
                        dotaMatchId)
                .stream()
                .findFirst();
    }

    public MatchImport createProcessing(String dotaMatchId, UUID requestedBy) {
        return jdbcTemplate.queryForObject(
                """
                insert into public.match_imports (
                  dota_match_id,
                  status,
                  requested_by,
                  started_at,
                  locked_at,
                  requested_at,
                  attempt_count
                )
                values (?, 'processing', ?, now(), now(), now(), 1)
                """ + returningSql(),
                this::mapMatchImport,
                dotaMatchId,
                requestedBy);
    }

    public Optional<MatchImport> markProcessing(UUID importId) {
        return jdbcTemplate.query(
                        """
                        update public.match_imports
                        set
                          status = 'processing',
                          attempt_count = attempt_count + 1,
                          started_at = now(),
                          completed_at = null,
                          locked_at = now(),
                          error_code = null,
                          error_message = null,
                          updated_at = now()
                        where id = ?
                        """ + returningSql(),
                        this::mapMatchImport,
                        importId)
                .stream()
                .findFirst();
    }

    @Transactional
    public Optional<MatchImport> markReady(
            UUID importId,
            String rawResponse,
            String normalizedPayload,
            List<MatchPlayerImport> players
    ) {
        replacePlayers(importId, players);

        return jdbcTemplate.query(
                        """
                        update public.match_imports
                        set
                          status = 'ready',
                          raw_response = cast(? as jsonb),
                          normalized_payload = cast(? as jsonb),
                          error_code = null,
                          error_message = null,
                          completed_at = now(),
                          locked_at = null,
                          updated_at = now()
                        where id = ?
                        """ + returningSql(),
                        this::mapMatchImport,
                        rawResponse,
                        normalizedPayload,
                        importId)
                .stream()
                .findFirst();
    }

    private void replacePlayers(UUID importId, List<MatchPlayerImport> players) {
        jdbcTemplate.update("delete from public.match_players where match_import_id = ?", importId);

        for (MatchPlayerImport player : players) {
            jdbcTemplate.update(
                    """
                    insert into public.match_players (
                      match_import_id,
                      match_id,
                      match_game_id,
                      hero_id,
                      steam_account_id,
                      player_slot,
                      is_radiant,
                      is_winner,
                      kills,
                      deaths,
                      assists,
                      last_hits,
                      denies,
                      gold_per_min,
                      xp_per_min,
                      net_worth,
                      hero_damage,
                      tower_damage,
                      hero_healing,
                      level,
                      duration_seconds,
                      raw_player
                    )
                    select
                      mi.id,
                      mi.match_id,
                      mi.match_game_id,
                      h.id,
                      ?,
                      ?,
                      ?,
                      ?,
                      ?,
                      ?,
                      ?,
                      ?,
                      ?,
                      ?,
                      ?,
                      ?,
                      ?,
                      ?,
                      ?,
                      ?,
                      ?,
                      cast(? as jsonb)
                    from public.match_imports mi
                    left join public.heroes h on h.dota_hero_id = ?
                    where mi.id = ?
                    """,
                    player.steamAccountId(),
                    player.playerSlot(),
                    player.radiant(),
                    player.winner(),
                    player.kills(),
                    player.deaths(),
                    player.assists(),
                    player.lastHits(),
                    player.denies(),
                    player.goldPerMinute(),
                    player.experiencePerMinute(),
                    player.netWorth(),
                    player.heroDamage(),
                    player.towerDamage(),
                    player.heroHealing(),
                    player.level(),
                    player.durationSeconds(),
                    player.rawPlayer(),
                    player.dotaHeroId(),
                    importId);
        }
    }

    public Optional<MatchImport> markError(UUID importId, String errorMessage) {
        return markError(importId, null, errorMessage);
    }

    public Optional<MatchImport> markError(UUID importId, OpenDotaErrorCode errorCode, String errorMessage) {
        return jdbcTemplate.query(
                        """
                        update public.match_imports
                        set
                          status = 'error',
                          error_code = ?,
                          error_message = ?,
                          completed_at = now(),
                          locked_at = null,
                          updated_at = now()
                        where id = ?
                        """ + returningSql(),
                        this::mapMatchImport,
                        errorCode == null ? null : errorCode.name(),
                        errorMessage,
                        importId)
                .stream()
                .findFirst();
    }

    private String selectSql() {
        return """
                select
                  id,
                  match_id,
                  match_game_id,
                  dota_match_id,
                  status::text as status,
                  requested_by,
                  error_code,
                  error_message,
                  started_at,
                  completed_at,
                  created_at,
                  updated_at
                from public.match_imports
                """;
    }

    private String returningSql() {
        return """
                returning
                  id,
                  match_id,
                  match_game_id,
                  dota_match_id,
                  status::text as status,
                  requested_by,
                  error_code,
                  error_message,
                  started_at,
                  completed_at,
                  created_at,
                  updated_at
                """;
    }

    private MatchImport mapMatchImport(ResultSet resultSet, int rowNumber) throws SQLException {
        return new MatchImport(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("match_id", UUID.class),
                resultSet.getObject("match_game_id", UUID.class),
                resultSet.getString("dota_match_id"),
                MatchImportStatus.fromDatabaseValue(resultSet.getString("status")),
                resultSet.getObject("requested_by", UUID.class),
                errorCode(resultSet.getString("error_code")),
                resultSet.getString("error_message"),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getObject("completed_at", OffsetDateTime.class),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class));
    }

    private OpenDotaErrorCode errorCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return OpenDotaErrorCode.valueOf(value);
    }
}
