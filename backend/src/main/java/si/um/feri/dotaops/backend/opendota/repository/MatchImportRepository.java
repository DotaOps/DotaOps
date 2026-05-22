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
import si.um.feri.dotaops.backend.opendota.domain.MatchImportEvent;
import si.um.feri.dotaops.backend.opendota.domain.MatchGameImport;
import si.um.feri.dotaops.backend.opendota.domain.MatchPlayerImport;
import si.um.feri.dotaops.backend.opendota.domain.MatchImportStatus;
import si.um.feri.dotaops.backend.opendota.domain.NormalizedMatchImport;
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

    public Optional<MatchImport> findById(UUID importId) {
        return jdbcTemplate.query(
                        selectSql() + """
                        where id = ?
                        limit 1
                        """,
                        this::mapMatchImport,
                        importId)
                .stream()
                .findFirst();
    }

    public List<MatchImportEvent> findEvents(UUID importId) {
        return jdbcTemplate.query(
                """
                select
                  id,
                  match_import_id,
                  status::text as event_type,
                  message,
                  error_code,
                  created_by,
                  created_at
                from public.match_import_events
                where match_import_id = ?
                order by created_at asc, id asc
                """,
                this::mapMatchImportEvent,
                importId);
    }

    @Transactional
    public MatchImport createQueued(String dotaMatchId, UUID requestedBy) {
        MatchImport matchImport = jdbcTemplate.queryForObject(
                """
                insert into public.match_imports (
                  dota_match_id,
                  status,
                  requested_by,
                  requested_at,
                  attempt_count
                )
                values (?, 'queued', ?, now(), 0)
                """ + returningSql(),
                this::mapMatchImport,
                dotaMatchId,
                requestedBy);
        ensureMatchGameForImport(matchImport.id(), MatchImportStatus.QUEUED);
        MatchImport linkedImport = findById(matchImport.id()).orElse(matchImport);

        appendEvent(
                linkedImport.id(),
                MatchImportStatus.QUEUED,
                "Match import queued.",
                null,
                requestedBy);

        return linkedImport;
    }

    @Transactional
    public Optional<MatchImport> markProcessing(UUID importId, UUID requestedBy, String message) {
        ensureMatchGameForImport(importId, MatchImportStatus.PROCESSING);

        Optional<MatchImport> matchImport = jdbcTemplate.query(
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
                                  and status in ('queued', 'error')
                                """ + returningSql(),
                                this::mapMatchImport,
                                importId)
                        .stream()
                        .findFirst();

        matchImport.ifPresent(ignored -> appendEvent(
                importId,
                MatchImportStatus.PROCESSING,
                message,
                null,
                requestedBy));

        return matchImport;
    }

    @Transactional
    public Optional<MatchImport> markReady(
            UUID importId,
            String rawResponse,
            String normalizedPayload,
            List<MatchPlayerImport> players
    ) {
        return markReady(
                importId,
                new NormalizedMatchImport(
                        new MatchGameImport(
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
                                rawResponse,
                                normalizedPayload),
                        players));
    }

    @Transactional
    public Optional<MatchImport> markReady(UUID importId, NormalizedMatchImport normalizedMatch) {
        UUID matchGameId = ensureMatchGameForImport(importId, MatchImportStatus.PROCESSING);
        updateMatchGame(matchGameId, normalizedMatch.matchGame());
        upsertPlayers(importId, normalizedMatch.players());

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
                        normalizedMatch.matchGame().rawResponse(),
                        normalizedMatch.matchGame().normalizedPayload(),
                        importId)
                .stream()
                .findFirst()
                .map(matchImport -> {
                    appendEvent(
                            importId,
                            MatchImportStatus.READY,
                            "Match import completed.",
                            null,
                            matchImport.requestedBy());
                    return matchImport;
                });
    }

    private void appendEvent(
            UUID importId,
            MatchImportStatus status,
            String message,
            OpenDotaErrorCode errorCode,
            UUID createdBy
    ) {
        jdbcTemplate.update(
                """
                insert into public.match_import_events (
                  match_import_id,
                  status,
                  message,
                  error_code,
                  created_by
                )
                values (?, cast(? as public.dotaops_import_status), ?, ?, ?)
                """,
                importId,
                status.databaseValue(),
                message,
                errorCode == null ? null : errorCode.name(),
                createdBy);
    }

    private void upsertPlayers(UUID importId, List<MatchPlayerImport> players) {
        for (MatchPlayerImport player : players) {
            jdbcTemplate.update(
                    """
                    insert into public.match_players (
                      match_import_id,
                      match_id,
                      match_game_id,
                      team_id,
                      profile_id,
                      hero_id,
                      dota_hero_id,
                      dota_account_id,
                      steam_account_id,
                      player_slot,
                      team_side,
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
                      items,
                      raw_player
                    )
                    select
                      mi.id,
                      mi.match_id,
                      mi.match_game_id,
                      case cast(? as text)
                        when 'RADIANT' then mg.radiant_team_id
                        when 'DIRE' then mg.dire_team_id
                        else null
                      end,
                      p.id,
                      h.id,
                      ?,
                      ?,
                      coalesce(?, p.steam_id),
                      ?,
                      cast(? as text),
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
                      cast(? as jsonb),
                      cast(? as jsonb)
                    from public.match_imports mi
                    left join public.match_games mg on mg.id = mi.match_game_id
                    left join public.heroes h on h.dota_hero_id = ?
                    left join public.profiles p on p.opendota_account_id = ?
                    where mi.id = ?
                    on conflict (match_game_id, player_slot)
                      where match_game_id is not null
                    do update set
                      match_import_id = excluded.match_import_id,
                      match_id = excluded.match_id,
                      team_id = excluded.team_id,
                      profile_id = excluded.profile_id,
                      hero_id = excluded.hero_id,
                      dota_hero_id = excluded.dota_hero_id,
                      dota_account_id = excluded.dota_account_id,
                      steam_account_id = excluded.steam_account_id,
                      team_side = excluded.team_side,
                      is_radiant = excluded.is_radiant,
                      is_winner = excluded.is_winner,
                      kills = excluded.kills,
                      deaths = excluded.deaths,
                      assists = excluded.assists,
                      last_hits = excluded.last_hits,
                      denies = excluded.denies,
                      gold_per_min = excluded.gold_per_min,
                      xp_per_min = excluded.xp_per_min,
                      net_worth = excluded.net_worth,
                      hero_damage = excluded.hero_damage,
                      tower_damage = excluded.tower_damage,
                      hero_healing = excluded.hero_healing,
                      level = excluded.level,
                      duration_seconds = excluded.duration_seconds,
                      items = excluded.items,
                      raw_player = excluded.raw_player,
                      updated_at = now()
                    """,
                    player.teamSide(),
                    player.dotaHeroId(),
                    player.dotaAccountId(),
                    player.steamAccountId(),
                    player.playerSlot(),
                    player.teamSide(),
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
                    player.items(),
                    player.rawPlayer(),
                    player.dotaHeroId(),
                    player.dotaAccountId(),
                    importId);
        }
    }

    public Optional<MatchImport> markError(UUID importId, String errorMessage) {
        return markError(importId, null, errorMessage);
    }

    public Optional<MatchImport> markError(UUID importId, OpenDotaErrorCode errorCode, String errorMessage) {
        ensureMatchGameForImport(importId, MatchImportStatus.ERROR);

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
                .findFirst()
                .map(matchImport -> {
                    appendEvent(
                            importId,
                            MatchImportStatus.ERROR,
                            errorMessage,
                            errorCode,
                            matchImport.requestedBy());
                    return matchImport;
                });
    }

    private UUID ensureMatchGameForImport(UUID importId, MatchImportStatus status) {
        MatchImportLink link = jdbcTemplate.queryForObject(
                """
                select id, match_id, match_game_id, dota_match_id
                from public.match_imports
                where id = ?
                """,
                this::mapMatchImportLink,
                importId);

        UUID matchGameId = link.matchGameId();
        if (matchGameId == null) {
            matchGameId = findMatchGameIdByDotaMatchId(link.dotaMatchId())
                    .orElseGet(() -> createStandaloneMatchGame(link.matchId(), link.dotaMatchId(), status));
            jdbcTemplate.update(
                    """
                    update public.match_imports
                    set match_game_id = ?,
                        updated_at = now()
                    where id = ?
                    """,
                    matchGameId,
                    importId);
        }

        jdbcTemplate.update(
                """
                update public.match_games
                set import_status = cast(? as public.dotaops_import_status),
                    updated_at = now()
                where id = ?
                """,
                status.databaseValue(),
                matchGameId);

        return matchGameId;
    }

    private Optional<UUID> findMatchGameIdByDotaMatchId(String dotaMatchId) {
        return jdbcTemplate.query(
                        """
                        select id
                        from public.match_games
                        where dota_match_id = ?
                        limit 1
                        """,
                        (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                        dotaMatchId)
                .stream()
                .findFirst();
    }

    private UUID createStandaloneMatchGame(UUID matchId, String dotaMatchId, MatchImportStatus status) {
        return jdbcTemplate.queryForObject(
                """
                insert into public.match_games (
                  match_id,
                  game_number,
                  status,
                  import_status,
                  dota_match_id
                )
                values (?, 1, 'scheduled', cast(? as public.dotaops_import_status), ?)
                on conflict (dota_match_id)
                do update set
                  import_status = excluded.import_status,
                  updated_at = now()
                returning id
                """,
                UUID.class,
                matchId,
                status.databaseValue(),
                dotaMatchId);
    }

    private void updateMatchGame(UUID matchGameId, MatchGameImport matchGame) {
        jdbcTemplate.update(
                """
                update public.match_games
                set
                  dota_match_id = coalesce(dota_match_id, ?),
                  duration_seconds = ?,
                  started_at = ?,
                  finished_at = ?,
                  radiant_win = ?,
                  game_mode = ?,
                  lobby_type = ?,
                  radiant_score = ?,
                  dire_score = ?,
                  winner_side = cast(? as text),
                  raw_response = cast(? as jsonb),
                  normalized_payload = cast(? as jsonb),
                  raw_summary = cast(? as jsonb),
                  updated_at = now()
                where id = ?
                """,
                matchGame.dotaMatchId(),
                matchGame.durationSeconds(),
                matchGame.startedAt(),
                matchGame.finishedAt(),
                matchGame.radiantWin(),
                matchGame.gameMode(),
                matchGame.lobbyType(),
                matchGame.radiantScore(),
                matchGame.direScore(),
                matchGame.winnerSide(),
                matchGame.rawResponse(),
                matchGame.normalizedPayload(),
                matchGame.normalizedPayload(),
                matchGameId);
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

    private MatchImportLink mapMatchImportLink(ResultSet resultSet, int rowNumber) throws SQLException {
        return new MatchImportLink(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("match_id", UUID.class),
                resultSet.getObject("match_game_id", UUID.class),
                resultSet.getString("dota_match_id"));
    }

    private MatchImportEvent mapMatchImportEvent(ResultSet resultSet, int rowNumber) throws SQLException {
        return new MatchImportEvent(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("match_import_id", UUID.class),
                MatchImportStatus.fromDatabaseValue(resultSet.getString("event_type")),
                resultSet.getString("message"),
                errorCode(resultSet.getString("error_code")),
                resultSet.getObject("created_by", UUID.class),
                resultSet.getObject("created_at", OffsetDateTime.class));
    }

    private OpenDotaErrorCode errorCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return OpenDotaErrorCode.valueOf(value);
    }

    private record MatchImportLink(
            UUID id,
            UUID matchId,
            UUID matchGameId,
            String dotaMatchId
    ) {
    }
}
