package si.um.feri.dotaops.backend.team.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import si.um.feri.dotaops.backend.team.domain.TeamManualPlayer;

@Repository
public class TeamManualPlayerRepository {

    private final JdbcTemplate jdbcTemplate;

    public TeamManualPlayerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TeamManualPlayer> findByTeamId(UUID teamId) {
        return jdbcTemplate.query(
                selectManualPlayerSql() + """
                where tmp.team_id = ?
                order by tmp.created_at asc, tmp.id asc
                """,
                this::mapManualPlayer,
                teamId);
    }

    public Optional<TeamManualPlayer> findById(UUID manualPlayerId) {
        return jdbcTemplate.query(
                        selectManualPlayerSql() + """
                        where tmp.id = ?
                        limit 1
                        """,
                        this::mapManualPlayer,
                        manualPlayerId)
                .stream()
                .findFirst();
    }

    public int countByTeamId(UUID teamId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from public.team_manual_players
                where team_id = ?
                """,
                Integer.class,
                teamId);

        return count == null ? 0 : count;
    }

    public TeamManualPlayer create(CreateTeamManualPlayerCommand command) {
        return jdbcTemplate.queryForObject(
                """
                insert into public.team_manual_players (
                  team_id,
                  display_name,
                  nickname,
                  note
                )
                values (?, ?, ?, ?)
                returning
                  id,
                  team_id,
                  display_name,
                  nickname,
                  note,
                  created_at,
                  updated_at
                """,
                this::mapManualPlayer,
                command.teamId(),
                command.displayName(),
                command.nickname(),
                command.note());
    }

    public Optional<TeamManualPlayer> update(
            UUID teamId,
            UUID manualPlayerId,
            UpdateTeamManualPlayerCommand command
    ) {
        return jdbcTemplate.query(
                        """
                        update public.team_manual_players
                        set
                          display_name = case when ? then ? else display_name end,
                          nickname = case when ? then ? else nickname end,
                          note = case when ? then ? else note end,
                          updated_at = now()
                        where id = ?
                          and team_id = ?
                        returning
                          id,
                          team_id,
                          display_name,
                          nickname,
                          note,
                          created_at,
                          updated_at
                        """,
                        this::mapManualPlayer,
                        command.displayNamePresent(),
                        command.displayName(),
                        command.nicknamePresent(),
                        command.nickname(),
                        command.notePresent(),
                        command.note(),
                        manualPlayerId,
                        teamId)
                .stream()
                .findFirst();
    }

    public Optional<TeamManualPlayer> delete(UUID teamId, UUID manualPlayerId) {
        return jdbcTemplate.query(
                        """
                        delete from public.team_manual_players
                        where id = ?
                          and team_id = ?
                        returning
                          id,
                          team_id,
                          display_name,
                          nickname,
                          note,
                          created_at,
                          updated_at
                        """,
                        this::mapManualPlayer,
                        manualPlayerId,
                        teamId)
                .stream()
                .findFirst();
    }

    private String selectManualPlayerSql() {
        return """
                select
                  tmp.id,
                  tmp.team_id,
                  tmp.display_name,
                  tmp.nickname,
                  tmp.note,
                  tmp.created_at,
                  tmp.updated_at
                from public.team_manual_players tmp
                """;
    }

    private TeamManualPlayer mapManualPlayer(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TeamManualPlayer(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("team_id", UUID.class),
                resultSet.getString("display_name"),
                resultSet.getString("nickname"),
                resultSet.getString("note"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class));
    }
}
