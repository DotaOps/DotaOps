package si.um.feri.dotaops.backend.team.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import si.um.feri.dotaops.backend.team.domain.TeamJoinRequest;
import si.um.feri.dotaops.backend.team.domain.TeamJoinRequestStatus;

@Repository
public class TeamJoinRequestRepository {

    private final JdbcTemplate jdbcTemplate;

    public TeamJoinRequestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TeamJoinRequest> findByTeamId(UUID teamId, TeamJoinRequestStatus status) {
        return jdbcTemplate.query(
                selectJoinRequestSql() + """
                where tjr.team_id = ?
                  and (
                    cast(? as text) is null
                    or tjr.status = cast(? as public.dotaops_team_join_request_status)
                  )
                order by tjr.created_at desc, tjr.id desc
                limit 100
                """,
                this::mapJoinRequest,
                teamId,
                status == null ? null : status.databaseValue(),
                status == null ? null : status.databaseValue());
    }

    public List<TeamJoinRequest> findByRequesterProfileId(UUID requesterProfileId, TeamJoinRequestStatus status) {
        return jdbcTemplate.query(
                selectJoinRequestSql() + """
                where tjr.requester_profile_id = ?
                  and (
                    cast(? as text) is null
                    or tjr.status = cast(? as public.dotaops_team_join_request_status)
                  )
                order by tjr.created_at desc, tjr.id desc
                limit 100
                """,
                this::mapJoinRequest,
                requesterProfileId,
                status == null ? null : status.databaseValue(),
                status == null ? null : status.databaseValue());
    }

    public Optional<TeamJoinRequest> findById(UUID requestId) {
        return jdbcTemplate.query(
                        selectJoinRequestSql() + """
                        where tjr.id = ?
                        limit 1
                        """,
                        this::mapJoinRequest,
                        requestId)
                .stream()
                .findFirst();
    }

    public Optional<TeamJoinRequest> findPendingByTeamAndRequester(UUID teamId, UUID requesterProfileId) {
        return jdbcTemplate.query(
                        selectJoinRequestSql() + """
                        where tjr.team_id = ?
                          and tjr.requester_profile_id = ?
                          and tjr.status = 'pending'
                        limit 1
                        """,
                        this::mapJoinRequest,
                        teamId,
                        requesterProfileId)
                .stream()
                .findFirst();
    }

    public TeamJoinRequest create(CreateTeamJoinRequestCommand command) {
        return jdbcTemplate.queryForObject(
                """
                insert into public.team_join_requests (
                  team_id,
                  requester_profile_id,
                  message
                )
                values (?, ?, ?)
                """ + returningJoinRequestSql(),
                this::mapJoinRequest,
                command.teamId(),
                command.requesterProfileId(),
                command.message());
    }

    public Optional<TeamJoinRequest> accept(UUID requestId, UUID resolvedByProfileId) {
        return resolve(requestId, TeamJoinRequestStatus.ACCEPTED, resolvedByProfileId);
    }

    public Optional<TeamJoinRequest> decline(UUID requestId, UUID resolvedByProfileId) {
        return resolve(requestId, TeamJoinRequestStatus.DECLINED, resolvedByProfileId);
    }

    public Optional<TeamJoinRequest> cancel(UUID requestId, UUID requesterProfileId) {
        return resolve(requestId, TeamJoinRequestStatus.CANCELLED, requesterProfileId);
    }

    public int cancelPendingByTeamId(UUID teamId, UUID resolvedByProfileId) {
        return jdbcTemplate.update(
                """
                update public.team_join_requests
                set
                  status = 'cancelled',
                  resolved_at = now(),
                  resolved_by_profile_id = ?,
                  updated_at = now()
                where team_id = ?
                  and status = 'pending'
                """,
                resolvedByProfileId,
                teamId);
    }

    private Optional<TeamJoinRequest> resolve(
            UUID requestId,
            TeamJoinRequestStatus status,
            UUID resolvedByProfileId
    ) {
        return jdbcTemplate.query(
                        """
                        update public.team_join_requests
                        set
                          status = cast(? as public.dotaops_team_join_request_status),
                          resolved_at = now(),
                          resolved_by_profile_id = ?,
                          updated_at = now()
                        where id = ?
                          and status = 'pending'
                        """ + returningJoinRequestSql(),
                        this::mapJoinRequest,
                        status.databaseValue(),
                        resolvedByProfileId,
                        requestId)
                .stream()
                .findFirst();
    }

    private String selectJoinRequestSql() {
        return """
                select
                  tjr.id,
                  tjr.team_id,
                  t.name as team_name,
                  t.slug as team_slug,
                  tjr.requester_profile_id,
                  coalesce(requester.display_name, requester.nickname) as requester_display_name,
                  tjr.message,
                  tjr.status::text as status,
                  tjr.created_at,
                  tjr.updated_at,
                  tjr.resolved_at,
                  tjr.resolved_by_profile_id,
                  coalesce(resolver.display_name, resolver.nickname) as resolved_by_display_name
                from public.team_join_requests tjr
                join public.teams t on t.id = tjr.team_id
                join public.profiles requester on requester.id = tjr.requester_profile_id
                left join public.profiles resolver on resolver.id = tjr.resolved_by_profile_id
                """;
    }

    private String returningJoinRequestSql() {
        return """
                returning
                  id,
                  team_id,
                  (
                    select t.name
                    from public.teams t
                    where t.id = team_id
                  ) as team_name,
                  (
                    select t.slug
                    from public.teams t
                    where t.id = team_id
                  ) as team_slug,
                  requester_profile_id,
                  (
                    select coalesce(p.display_name, p.nickname)
                    from public.profiles p
                    where p.id = requester_profile_id
                  ) as requester_display_name,
                  message,
                  status::text as status,
                  created_at,
                  updated_at,
                  resolved_at,
                  resolved_by_profile_id,
                  (
                    select coalesce(p.display_name, p.nickname)
                    from public.profiles p
                    where p.id = resolved_by_profile_id
                  ) as resolved_by_display_name
                """;
    }

    private TeamJoinRequest mapJoinRequest(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TeamJoinRequest(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("team_id", UUID.class),
                resultSet.getString("team_name"),
                resultSet.getString("team_slug"),
                resultSet.getObject("requester_profile_id", UUID.class),
                resultSet.getString("requester_display_name"),
                resultSet.getString("message"),
                TeamJoinRequestStatus.fromDatabaseValue(resultSet.getString("status")),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class),
                resultSet.getObject("resolved_at", OffsetDateTime.class),
                resultSet.getObject("resolved_by_profile_id", UUID.class),
                resultSet.getString("resolved_by_display_name"));
    }
}
