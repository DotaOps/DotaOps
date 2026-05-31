package si.um.feri.dotaops.backend.team.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import si.um.feri.dotaops.backend.team.domain.TeamMember;
import si.um.feri.dotaops.backend.team.domain.TeamMemberRole;

@Repository
public class TeamMemberRepository {

    private final JdbcTemplate jdbcTemplate;

    public TeamMemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TeamMember> findActiveByTeamId(UUID teamId) {
        return jdbcTemplate.query(
                selectTeamMemberSql() + """
                where tm.team_id = ?
                  and tm.is_active = true
                order by tm.joined_at asc, tm.id asc
                """,
                this::mapTeamMember,
                teamId);
    }

    public Optional<TeamMember> findById(UUID memberId) {
        return jdbcTemplate.query(
                        selectTeamMemberSql() + """
                        where tm.id = ?
                        limit 1
                        """,
                        this::mapTeamMember,
                        memberId)
                .stream()
                .findFirst();
    }

    public boolean existsActive(UUID teamId, UUID profileId) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                select exists (
                  select 1
                  from public.team_members
                  where team_id = ?
                    and profile_id = ?
                    and is_active = true
                )
                """,
                Boolean.class,
                teamId,
                profileId);

        return Boolean.TRUE.equals(exists);
    }

    public int countActiveByTeamId(UUID teamId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from public.team_members
                where team_id = ?
                  and is_active = true
                """,
                Integer.class,
                teamId);

        return count == null ? 0 : count;
    }

    public TeamMember create(CreateTeamMemberCommand command) {
        return jdbcTemplate.queryForObject(
                """
                insert into public.team_members (
                  team_id,
                  profile_id,
                  member_role
                )
                values (?, ?, cast(? as public.dotaops_team_member_role))
                returning
                  id,
                  team_id,
                  profile_id,
                  (
                    select p.nickname
                    from public.profiles p
                    where p.id = profile_id
                  ) as nickname,
                  (
                    select p.display_name
                    from public.profiles p
                    where p.id = profile_id
                  ) as display_name,
                  (
                    select p.avatar_url
                    from public.profiles p
                    where p.id = profile_id
                  ) as avatar_url,
                  member_role::text as member_role,
                  is_active,
                  joined_at,
                  left_at,
                  updated_at
                """,
                this::mapTeamMember,
                command.teamId(),
                command.profileId(),
                command.role().databaseValue());
    }

    public Optional<TeamMember> updateRole(UUID teamId, UUID memberId, TeamMemberRole role) {
        return jdbcTemplate.query(
                        """
                        update public.team_members
                        set
                          member_role = cast(? as public.dotaops_team_member_role),
                          updated_at = now()
                        where id = ?
                          and team_id = ?
                          and is_active = true
                        returning
                          id,
                          team_id,
                          profile_id,
                          (
                            select p.nickname
                            from public.profiles p
                            where p.id = profile_id
                          ) as nickname,
                          (
                            select p.display_name
                            from public.profiles p
                            where p.id = profile_id
                          ) as display_name,
                          (
                            select p.avatar_url
                            from public.profiles p
                            where p.id = profile_id
                          ) as avatar_url,
                          member_role::text as member_role,
                          is_active,
                          joined_at,
                          left_at,
                          updated_at
                        """,
                        this::mapTeamMember,
                        role.databaseValue(),
                        memberId,
                        teamId)
                .stream()
                .findFirst();
    }

    public Optional<TeamMember> deactivate(UUID teamId, UUID memberId) {
        return jdbcTemplate.query(
                        """
                        update public.team_members
                        set
                          is_active = false,
                          left_at = coalesce(left_at, now()),
                          updated_at = now()
                        where id = ?
                          and team_id = ?
                          and is_active = true
                        returning
                          id,
                          team_id,
                          profile_id,
                          (
                            select p.nickname
                            from public.profiles p
                            where p.id = profile_id
                          ) as nickname,
                          (
                            select p.display_name
                            from public.profiles p
                            where p.id = profile_id
                          ) as display_name,
                          (
                            select p.avatar_url
                            from public.profiles p
                            where p.id = profile_id
                          ) as avatar_url,
                          member_role::text as member_role,
                          is_active,
                          joined_at,
                          left_at,
                          updated_at
                        """,
                        this::mapTeamMember,
                        memberId,
                        teamId)
                .stream()
                .findFirst();
    }

    private String selectTeamMemberSql() {
        return """
                select
                  tm.id,
                  tm.team_id,
                  tm.profile_id,
                  p.nickname,
                  p.display_name,
                  p.avatar_url,
                  tm.member_role::text as member_role,
                  tm.is_active,
                  tm.joined_at,
                  tm.left_at,
                  tm.updated_at
                from public.team_members tm
                join public.profiles p on p.id = tm.profile_id
                """;
    }

    private TeamMember mapTeamMember(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TeamMember(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("team_id", UUID.class),
                resultSet.getObject("profile_id", UUID.class),
                resultSet.getString("nickname"),
                resultSet.getString("display_name"),
                resultSet.getString("avatar_url"),
                TeamMemberRole.fromDatabaseValue(resultSet.getString("member_role")),
                resultSet.getBoolean("is_active"),
                resultSet.getObject("joined_at", OffsetDateTime.class),
                resultSet.getObject("left_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class));
    }
}
