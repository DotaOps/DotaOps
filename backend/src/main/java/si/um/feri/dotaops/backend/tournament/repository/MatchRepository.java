package si.um.feri.dotaops.backend.tournament.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import si.um.feri.dotaops.backend.tournament.domain.MatchStatus;
import si.um.feri.dotaops.backend.tournament.domain.MatchTeamCaptain;
import si.um.feri.dotaops.backend.tournament.domain.TournamentMatch;

@Repository
public class MatchRepository {

    private final JdbcTemplate jdbcTemplate;

    public MatchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<TournamentMatch> findById(UUID matchId) {
        return jdbcTemplate.query(
                        selectMatchSql() + """
                        where m.id = ?
                        limit 1
                        """,
                        this::mapMatch,
                        matchId)
                .stream()
                .findFirst();
    }

    public Optional<TournamentMatch> findPublicById(UUID matchId) {
        return jdbcTemplate.query(
                        selectMatchSql() + """
                        join public.tournaments t on t.id = m.tournament_id
                        where m.id = ?
                          and t.is_public = true
                          and t.status in ('registration', 'published', 'live', 'finished')
                        limit 1
                        """,
                        this::mapMatch,
                        matchId)
                .stream()
                .findFirst();
    }

    public List<TournamentMatch> findPublicByTournamentId(UUID tournamentId) {
        return jdbcTemplate.query(
                selectMatchSql() + """
                join public.tournaments t on t.id = m.tournament_id
                where m.tournament_id = ?
                  and t.is_public = true
                  and t.status in ('registration', 'published', 'live', 'finished')
                order by m.stage_name asc, m.round_number asc, m.bracket_position asc nulls last, m.created_at asc, m.id asc
                """,
                this::mapMatch,
                tournamentId);
    }

    public List<TournamentMatch> findOrganizerByTournamentId(UUID tournamentId) {
        return jdbcTemplate.query(
                selectMatchSql() + """
                where m.tournament_id = ?
                order by m.stage_name asc, m.round_number asc, m.bracket_position asc nulls last, m.created_at asc, m.id asc
                """,
                this::mapMatch,
                tournamentId);
    }

    public List<MatchTeamCaptain> findTeamCaptainsByMatchId(UUID matchId) {
        return jdbcTemplate.query(
                """
                select distinct
                  team_id,
                  team_name,
                  captain_profile_id
                from (
                  select
                    ta.id as team_id,
                    ta.name as team_name,
                    ta.captain_profile_id
                  from public.matches m
                  join public.teams ta on ta.id = m.team_a_id
                  where m.id = ?
                  union all
                  select
                    tb.id as team_id,
                    tb.name as team_name,
                    tb.captain_profile_id
                  from public.matches m
                  join public.teams tb on tb.id = m.team_b_id
                  where m.id = ?
                ) participants
                where captain_profile_id is not null
                order by team_name asc, team_id asc
                """,
                (resultSet, rowNumber) -> new MatchTeamCaptain(
                        resultSet.getObject("team_id", UUID.class),
                        resultSet.getString("team_name"),
                        resultSet.getObject("captain_profile_id", UUID.class)),
                matchId,
                matchId);
    }

    public Optional<TournamentMatch> schedule(UUID matchId, OffsetDateTime scheduledAt) {
        return jdbcTemplate.query(
                        """
                        update public.matches
                        set
                          scheduled_at = ?,
                          status = 'scheduled',
                          updated_at = now()
                        where id = ?
                        """ + returningMatchSql(),
                        this::mapMatch,
                        scheduledAt,
                        matchId)
                .stream()
                .findFirst();
    }

    public Optional<TournamentMatch> start(UUID matchId, OffsetDateTime startedAt) {
        return jdbcTemplate.query(
                        """
                        update public.matches
                        set
                          status = 'live',
                          started_at = coalesce(started_at, ?),
                          updated_at = now()
                        where id = ?
                        """ + returningMatchSql(),
                        this::mapMatch,
                        startedAt,
                        matchId)
                .stream()
                .findFirst();
    }

    public Optional<TournamentMatch> cancel(UUID matchId, String cancellationReason, OffsetDateTime cancelledAt) {
        return jdbcTemplate.query(
                        """
                        update public.matches
                        set
                          status = 'cancelled',
                          cancelled_at = ?,
                          cancellation_reason = ?,
                          updated_at = now()
                        where id = ?
                        """ + returningMatchSql(),
                        this::mapMatch,
                        cancelledAt,
                        cancellationReason,
                        matchId)
                .stream()
                .findFirst();
    }

    public Optional<TournamentMatch> submitResult(
            UUID matchId,
            int scoreA,
            int scoreB,
            UUID winnerTeamId,
            OffsetDateTime finishedAt
    ) {
        return jdbcTemplate.query(
                        """
                        update public.matches
                        set
                          score_a = ?,
                          score_b = ?,
                          winner_team_id = ?,
                          status = 'finished',
                          finished_at = coalesce(finished_at, ?),
                          updated_at = now()
                        where id = ?
                        """ + returningMatchSql(),
                        this::mapMatch,
                        scoreA,
                        scoreB,
                        winnerTeamId,
                        finishedAt,
                        matchId)
                .stream()
                .findFirst();
    }

    public Optional<TournamentMatch> finish(UUID matchId, OffsetDateTime finishedAt) {
        return jdbcTemplate.query(
                        """
                        update public.matches
                        set
                          status = 'finished',
                          finished_at = coalesce(finished_at, ?),
                          updated_at = now()
                        where id = ?
                        """ + returningMatchSql(),
                        this::mapMatch,
                        finishedAt,
                        matchId)
                .stream()
                .findFirst();
    }

    private String selectMatchSql() {
        return """
                select
                  m.id,
                  m.tournament_id,
                  m.group_id,
                  m.round_number,
                  m.bracket_position,
                  m.stage_name,
                  m.round_name,
                  m.status::text as status,
                  m.team_a_id,
                  ta.name as team_a_name,
                  m.team_b_id,
                  tb.name as team_b_name,
                  m.score_a,
                  m.score_b,
                  m.winner_team_id,
                  tw.name as winner_team_name,
                  m.best_of,
                  m.scheduled_at,
                  m.started_at,
                  m.finished_at,
                  m.cancelled_at,
                  m.cancellation_reason,
                  m.created_at,
                  m.updated_at
                from public.matches m
                left join public.teams ta on ta.id = m.team_a_id
                left join public.teams tb on tb.id = m.team_b_id
                left join public.teams tw on tw.id = m.winner_team_id
                """;
    }

    private String returningMatchSql() {
        return """
                returning
                  id,
                  tournament_id,
                  group_id,
                  round_number,
                  bracket_position,
                  stage_name,
                  round_name,
                  status::text as status,
                  team_a_id,
                  (
                    select ta.name
                    from public.teams ta
                    where ta.id = team_a_id
                  ) as team_a_name,
                  team_b_id,
                  (
                    select tb.name
                    from public.teams tb
                    where tb.id = team_b_id
                  ) as team_b_name,
                  score_a,
                  score_b,
                  winner_team_id,
                  (
                    select tw.name
                    from public.teams tw
                    where tw.id = winner_team_id
                  ) as winner_team_name,
                  best_of,
                  scheduled_at,
                  started_at,
                  finished_at,
                  cancelled_at,
                  cancellation_reason,
                  created_at,
                  updated_at
                """;
    }

    private TournamentMatch mapMatch(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TournamentMatch(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tournament_id", UUID.class),
                resultSet.getObject("group_id", UUID.class),
                resultSet.getInt("round_number"),
                resultSet.getObject("bracket_position", Integer.class),
                resultSet.getString("stage_name"),
                resultSet.getString("round_name"),
                MatchStatus.fromDatabaseValue(resultSet.getString("status")),
                resultSet.getObject("team_a_id", UUID.class),
                resultSet.getString("team_a_name"),
                resultSet.getObject("team_b_id", UUID.class),
                resultSet.getString("team_b_name"),
                resultSet.getInt("score_a"),
                resultSet.getInt("score_b"),
                resultSet.getObject("winner_team_id", UUID.class),
                resultSet.getString("winner_team_name"),
                resultSet.getInt("best_of"),
                resultSet.getObject("scheduled_at", OffsetDateTime.class),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getObject("finished_at", OffsetDateTime.class),
                resultSet.getObject("cancelled_at", OffsetDateTime.class),
                resultSet.getString("cancellation_reason"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class));
    }
}
