package si.um.feri.dotaops.backend.team.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import si.um.feri.dotaops.backend.team.domain.TeamInvitation;
import si.um.feri.dotaops.backend.team.domain.TeamInvitationStatus;
import si.um.feri.dotaops.backend.team.domain.TeamMemberRole;

@Repository
public class TeamInvitationRepository {

    private final JdbcTemplate jdbcTemplate;

    public TeamInvitationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TeamInvitation> findByTeamId(UUID teamId, TeamInvitationStatus status) {
        return jdbcTemplate.query(
                selectTeamInvitationSql() + """
                where ti.team_id = ?
                  and (
                    cast(? as text) is null
                    or ti.status = cast(? as public.dotaops_invitation_status)
                  )
                order by ti.created_at desc, ti.id desc
                limit 100
                """,
                this::mapTeamInvitation,
                teamId,
                status == null ? null : status.databaseValue(),
                status == null ? null : status.databaseValue());
    }

    public List<TeamInvitation> findForInvitee(UUID profileId, String normalizedEmail, TeamInvitationStatus status) {
        return jdbcTemplate.query(
                selectTeamInvitationSql() + """
                where (
                  (
                    ti.invitee_profile_id is not null
                    and ti.invitee_email is not null
                    and ti.invitee_profile_id = ?
                    and cast(? as text) is not null
                    and lower(ti.invitee_email) = cast(? as text)
                  )
                  or (
                    ti.invitee_profile_id is not null
                    and ti.invitee_email is null
                    and ti.invitee_profile_id = ?
                  )
                  or (
                    ti.invitee_profile_id is null
                    and ti.invitee_email is not null
                    and cast(? as text) is not null
                    and lower(ti.invitee_email) = cast(? as text)
                  )
                )
                  and (
                    cast(? as text) is null
                    or ti.status = cast(? as public.dotaops_invitation_status)
                  )
                order by ti.created_at desc, ti.id desc
                limit 100
                """,
                this::mapTeamInvitation,
                profileId,
                normalizedEmail,
                normalizedEmail,
                profileId,
                normalizedEmail,
                normalizedEmail,
                status == null ? null : status.databaseValue(),
                status == null ? null : status.databaseValue());
    }

    public Optional<TeamInvitation> findById(UUID invitationId) {
        return jdbcTemplate.query(
                        selectTeamInvitationSql() + """
                        where ti.id = ?
                        limit 1
                        """,
                        this::mapTeamInvitation,
                        invitationId)
                .stream()
                .findFirst();
    }

    public Optional<TeamInvitation> findPendingByTeamAndInviteeProfile(UUID teamId, UUID inviteeProfileId) {
        return jdbcTemplate.query(
                        selectTeamInvitationSql() + """
                        where ti.team_id = ?
                          and ti.invitee_profile_id = ?
                          and ti.status = 'pending'
                        limit 1
                        """,
                        this::mapTeamInvitation,
                        teamId,
                        inviteeProfileId)
                .stream()
                .findFirst();
    }

    public Optional<TeamInvitation> findPendingByTeamAndInviteeEmail(UUID teamId, String normalizedEmail) {
        return jdbcTemplate.query(
                        selectTeamInvitationSql() + """
                        where ti.team_id = ?
                          and lower(ti.invitee_email) = ?
                          and ti.status = 'pending'
                        limit 1
                        """,
                        this::mapTeamInvitation,
                        teamId,
                        normalizedEmail)
                .stream()
                .findFirst();
    }

    public TeamInvitation create(CreateTeamInvitationCommand command) {
        return jdbcTemplate.queryForObject(
                """
                insert into public.team_invitations (
                  team_id,
                  inviter_profile_id,
                  invitee_profile_id,
                  invitee_email,
                  proposed_role,
                  expires_at
                )
                values (?, ?, ?, ?, cast(? as public.dotaops_team_member_role), ?)
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
                  inviter_profile_id,
                  (
                    select p.nickname
                    from public.profiles p
                    where p.id = inviter_profile_id
                  ) as inviter_nickname,
                  invitee_profile_id,
                  (
                    select p.nickname
                    from public.profiles p
                    where p.id = invitee_profile_id
                  ) as invitee_nickname,
                  invitee_email,
                  proposed_role::text as proposed_role,
                  status::text as status,
                  expires_at,
                  accepted_at,
                  created_at,
                  updated_at
                """,
                this::mapTeamInvitation,
                command.teamId(),
                command.inviterProfileId(),
                command.inviteeProfileId(),
                command.inviteeEmail(),
                command.proposedRole().databaseValue(),
                command.expiresAt());
    }

    public Optional<TeamInvitation> accept(UUID invitationId, UUID inviteeProfileId) {
        return updateStatus(invitationId, TeamInvitationStatus.ACCEPTED, inviteeProfileId);
    }

    public Optional<TeamInvitation> decline(UUID invitationId) {
        return updateStatus(invitationId, TeamInvitationStatus.DECLINED, null);
    }

    public Optional<TeamInvitation> cancel(UUID invitationId) {
        return updateStatus(invitationId, TeamInvitationStatus.CANCELLED, null);
    }

    public Optional<TeamInvitation> expire(UUID invitationId) {
        return jdbcTemplate.query(
                        statusUpdateSql("""
                        status = 'expired',
                        updated_at = now()
                        """) + """
                        where id = ?
                          and status = 'pending'
                        """ + returningTeamInvitationSql(),
                        this::mapTeamInvitation,
                        invitationId)
                .stream()
                .findFirst();
    }

    public int cancelPendingByTeamId(UUID teamId) {
        return jdbcTemplate.update(
                """
                update public.team_invitations
                set
                  status = 'cancelled',
                  updated_at = now()
                where team_id = ?
                  and status = 'pending'
                """,
                teamId);
    }

    private Optional<TeamInvitation> updateStatus(
            UUID invitationId,
            TeamInvitationStatus status,
            UUID inviteeProfileId
    ) {
        String acceptedFields = status == TeamInvitationStatus.ACCEPTED
                ? """
                  invitee_profile_id = coalesce(invitee_profile_id, ?),
                  accepted_at = now(),
                  """
                : "";

        Object[] params = status == TeamInvitationStatus.ACCEPTED
                ? new Object[] { inviteeProfileId, status.databaseValue(), invitationId }
                : new Object[] { status.databaseValue(), invitationId };

        return jdbcTemplate.query(
                        statusUpdateSql(acceptedFields + """
                        status = cast(? as public.dotaops_invitation_status),
                        updated_at = now()
                        """) + """
                        where id = ?
                          and status = 'pending'
                          and (
                            expires_at is null
                            or expires_at >= now()
                          )
                        """ + returningTeamInvitationSql(),
                        this::mapTeamInvitation,
                        params)
                .stream()
                .findFirst();
    }

    private String statusUpdateSql(String setClause) {
        return """
                update public.team_invitations
                set
                """ + setClause;
    }

    private String returningTeamInvitationSql() {
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
                  inviter_profile_id,
                  (
                    select p.nickname
                    from public.profiles p
                    where p.id = inviter_profile_id
                  ) as inviter_nickname,
                  invitee_profile_id,
                  (
                    select p.nickname
                    from public.profiles p
                    where p.id = invitee_profile_id
                  ) as invitee_nickname,
                  invitee_email,
                  proposed_role::text as proposed_role,
                  status::text as status,
                  expires_at,
                  accepted_at,
                  created_at,
                  updated_at
                """;
    }

    private String selectTeamInvitationSql() {
        return """
                select
                  ti.id,
                  ti.team_id,
                  t.name as team_name,
                  t.slug as team_slug,
                  ti.inviter_profile_id,
                  inviter.nickname as inviter_nickname,
                  ti.invitee_profile_id,
                  invitee.nickname as invitee_nickname,
                  ti.invitee_email,
                  ti.proposed_role::text as proposed_role,
                  ti.status::text as status,
                  ti.expires_at,
                  ti.accepted_at,
                  ti.created_at,
                  ti.updated_at
                from public.team_invitations ti
                join public.teams t on t.id = ti.team_id
                left join public.profiles inviter on inviter.id = ti.inviter_profile_id
                left join public.profiles invitee on invitee.id = ti.invitee_profile_id
                """;
    }

    private TeamInvitation mapTeamInvitation(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TeamInvitation(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("team_id", UUID.class),
                resultSet.getString("team_name"),
                resultSet.getString("team_slug"),
                resultSet.getObject("inviter_profile_id", UUID.class),
                resultSet.getString("inviter_nickname"),
                resultSet.getObject("invitee_profile_id", UUID.class),
                resultSet.getString("invitee_nickname"),
                resultSet.getString("invitee_email"),
                TeamMemberRole.fromDatabaseValue(resultSet.getString("proposed_role")),
                TeamInvitationStatus.fromDatabaseValue(resultSet.getString("status")),
                resultSet.getObject("expires_at", OffsetDateTime.class),
                resultSet.getObject("accepted_at", OffsetDateTime.class),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class));
    }
}
