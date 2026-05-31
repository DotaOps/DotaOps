package si.um.feri.dotaops.backend.tournament.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import si.um.feri.dotaops.backend.team.domain.TeamMemberRole;
import si.um.feri.dotaops.backend.tournament.domain.TournamentRegistration;
import si.um.feri.dotaops.backend.tournament.domain.TournamentRegistrationMember;
import si.um.feri.dotaops.backend.tournament.domain.TournamentRegistrationStatus;

@Repository
public class TournamentRegistrationRepository {

    private final JdbcTemplate jdbcTemplate;

    public TournamentRegistrationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TournamentRegistration> findByTournamentId(UUID tournamentId, TournamentRegistrationStatus status) {
        String statusValue = status == null ? null : status.databaseValue();

        return jdbcTemplate.query(
                selectRegistrationSql() + """
                where tr.tournament_id = ?
                  and (
                    cast(? as text) is null
                    or tr.status = cast(? as public.dotaops_registration_status)
                  )
                order by tr.seed_number nulls last, tr.created_at asc, tr.id asc
                """,
                this::mapRegistration,
                tournamentId,
                statusValue,
                statusValue);
    }

    public List<TournamentRegistration> findByTeamId(UUID teamId) {
        return jdbcTemplate.query(
                selectRegistrationSql() + """
                where tr.team_id = ?
                order by tr.created_at desc, tr.id desc
                """,
                this::mapRegistration,
                teamId);
    }

    public boolean existsByTournamentIdAndTeamId(UUID tournamentId, UUID teamId) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                select exists (
                  select 1
                  from public.tournament_registrations
                  where tournament_id = ?
                    and team_id = ?
                )
                """,
                Boolean.class,
                tournamentId,
                teamId);

        return Boolean.TRUE.equals(exists);
    }

    public Optional<TournamentRegistration> findById(UUID registrationId) {
        return jdbcTemplate.query(
                        selectRegistrationSql() + """
                        where tr.id = ?
                        limit 1
                        """,
                        this::mapRegistration,
                        registrationId)
                .stream()
                .findFirst();
    }

    public Optional<TournamentRegistration> findByIdAndTournamentId(UUID registrationId, UUID tournamentId) {
        return jdbcTemplate.query(
                        selectRegistrationSql() + """
                        where tr.id = ?
                          and tr.tournament_id = ?
                        limit 1
                        """,
                        this::mapRegistration,
                        registrationId,
                        tournamentId)
                .stream()
                .findFirst();
    }

    public int countActiveRosterMembers(UUID teamId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select
                  (
                    select count(*)
                    from public.team_members
                    where team_id = ?
                      and is_active = true
                  )
                  +
                  (
                    select count(*)
                    from public.team_manual_players
                    where team_id = ?
                  )
                """,
                Integer.class,
                teamId,
                teamId);

        return count == null ? 0 : count;
    }

    public long countApprovedRegistrations(UUID tournamentId, UUID excludingRegistrationId) {
        Long count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from public.tournament_registrations
                where tournament_id = ?
                  and status = 'approved'
                  and (cast(? as uuid) is null or id <> ?)
                """,
                Long.class,
                tournamentId,
                excludingRegistrationId,
                excludingRegistrationId);

        return count == null ? 0 : count;
    }

    public TournamentRegistration create(CreateTournamentRegistrationCommand command, int rosterSnapshotSize) {
        TournamentRegistration registration = jdbcTemplate.queryForObject(
                """
                insert into public.tournament_registrations (
                  tournament_id,
                  team_id,
                  captain_profile_id,
                  message,
                  contact_email
                )
                values (?, ?, ?, ?, ?)
                """ + returningRegistrationSql(),
                this::mapRegistration,
                command.tournamentId(),
                command.teamId(),
                command.captainProfileId(),
                command.message(),
                command.contactEmail());

        snapshotActiveRoster(registration.id(), command.teamId(), command.captainProfileId(), rosterSnapshotSize);

        return findById(registration.id()).orElse(registration);
    }

    public Optional<TournamentRegistration> updateStatus(
            UUID registrationId,
            UUID tournamentId,
            TournamentRegistrationStatus status,
            UUID reviewedBy,
            Integer seedNumber
    ) {
        return jdbcTemplate.query(
                        """
                        update public.tournament_registrations
                        set
                          status = cast(? as public.dotaops_registration_status),
                          reviewed_by = ?,
                          reviewed_at = now(),
                          seed_number = case
                            when cast(? as public.dotaops_registration_status) = 'approved' then ?
                            else null
                          end,
                          updated_at = now()
                        where id = ?
                          and tournament_id = ?
                        """ + returningRegistrationSql(),
                        this::mapRegistration,
                        status.databaseValue(),
                        reviewedBy,
                        status.databaseValue(),
                        seedNumber,
                        registrationId,
                        tournamentId)
                .stream()
                .findFirst();
    }

    public Optional<TournamentRegistration> checkIn(UUID registrationId, UUID tournamentId) {
        return jdbcTemplate.query(
                        """
                        update public.tournament_registrations
                        set
                          checked_in_at = coalesce(checked_in_at, now()),
                          updated_at = now()
                        where id = ?
                          and tournament_id = ?
                        """ + returningRegistrationSql(),
                        this::mapRegistration,
                        registrationId,
                        tournamentId)
                .stream()
                .findFirst();
    }

    public List<TournamentRegistrationMember> findMembers(UUID registrationId) {
        return jdbcTemplate.query(
                """
                select
                  trm.id,
                  trm.registration_id,
                  trm.profile_id,
                  coalesce(p.nickname, trm.manual_nickname) as nickname,
                  coalesce(p.display_name, trm.manual_display_name) as display_name,
                  p.avatar_url,
                  trm.team_member_id,
                  trm.manual_player_id,
                  trm.manual_note,
                  trm.member_role::text as member_role,
                  trm.is_starter,
                  trm.created_at,
                  trm.updated_at
                from public.tournament_registration_members trm
                left join public.profiles p on p.id = trm.profile_id
                where trm.registration_id = ?
                order by trm.is_starter desc, trm.created_at asc, trm.id asc
                """,
                this::mapRegistrationMember,
                registrationId);
    }

    private void snapshotActiveRoster(
            UUID registrationId,
            UUID teamId,
            UUID captainProfileId,
            int rosterSnapshotSize
    ) {
        jdbcTemplate.update(
                """
                insert into public.tournament_registration_members (
                  registration_id,
                  profile_id,
                  team_member_id,
                  member_role,
                  is_starter
                )
                select
                  ?,
                  tm.profile_id,
                  tm.id,
                  tm.member_role,
                  true
                from public.team_members tm
                where tm.team_id = ?
                  and tm.is_active = true
                order by
                  case when tm.profile_id = ? then 0 else 1 end,
                  tm.joined_at asc,
                  tm.id asc
                limit ?
                on conflict (registration_id, profile_id) do nothing
                """,
                registrationId,
                teamId,
                captainProfileId,
                rosterSnapshotSize);

        Integer activeProfileCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from public.team_members tm
                where tm.team_id = ?
                  and tm.is_active = true
                """,
                Integer.class,
                teamId);
        int remainingRosterSlots = Math.max(0, rosterSnapshotSize - (activeProfileCount == null ? 0 : activeProfileCount));
        if (remainingRosterSlots == 0) {
            return;
        }

        jdbcTemplate.update(
                """
                insert into public.tournament_registration_members (
                  registration_id,
                  profile_id,
                  team_member_id,
                  manual_player_id,
                  manual_display_name,
                  manual_nickname,
                  manual_note,
                  member_role,
                  is_starter
                )
                select
                  ?,
                  null,
                  null,
                  tmp.id,
                  tmp.display_name,
                  tmp.nickname,
                  tmp.note,
                  null,
                  true
                from public.team_manual_players tmp
                where tmp.team_id = ?
                order by tmp.created_at asc, tmp.id asc
                limit ?
                on conflict do nothing
                """,
                registrationId,
                teamId,
                remainingRosterSlots);
    }

    private String selectRegistrationSql() {
        return """
                select
                  tr.id,
                  tr.tournament_id,
                  t.slug as tournament_slug,
                  t.title as tournament_title,
                  tr.team_id,
                  tm.name as team_name,
                  tm.tag as team_tag,
                  tm.slug as team_slug,
                  tr.captain_profile_id,
                  cp.nickname as captain_nickname,
                  tr.status::text as status,
                  tr.message,
                  tr.reviewed_by,
                  rp.nickname as reviewed_by_nickname,
                  tr.reviewed_at,
                  tr.seed_number,
                  tr.checked_in_at,
                  tr.contact_email,
                  tr.created_at,
                  tr.updated_at
                from public.tournament_registrations tr
                join public.tournaments t on t.id = tr.tournament_id
                join public.teams tm on tm.id = tr.team_id
                left join public.profiles cp on cp.id = tr.captain_profile_id
                left join public.profiles rp on rp.id = tr.reviewed_by
                """;
    }

    private String returningRegistrationSql() {
        return """
                returning
                  id,
                  tournament_id,
                  (
                    select t.slug
                    from public.tournaments t
                    where t.id = tournament_id
                  ) as tournament_slug,
                  (
                    select t.title
                    from public.tournaments t
                    where t.id = tournament_id
                  ) as tournament_title,
                  team_id,
                  (
                    select tm.name
                    from public.teams tm
                    where tm.id = team_id
                  ) as team_name,
                  (
                    select tm.tag
                    from public.teams tm
                    where tm.id = team_id
                  ) as team_tag,
                  (
                    select tm.slug
                    from public.teams tm
                    where tm.id = team_id
                  ) as team_slug,
                  captain_profile_id,
                  (
                    select cp.nickname
                    from public.profiles cp
                    where cp.id = captain_profile_id
                  ) as captain_nickname,
                  status::text as status,
                  message,
                  reviewed_by,
                  (
                    select rp.nickname
                    from public.profiles rp
                    where rp.id = reviewed_by
                  ) as reviewed_by_nickname,
                  reviewed_at,
                  seed_number,
                  checked_in_at,
                  contact_email,
                  created_at,
                  updated_at
                """;
    }

    private TournamentRegistration mapRegistration(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TournamentRegistration(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tournament_id", UUID.class),
                resultSet.getString("tournament_slug"),
                resultSet.getString("tournament_title"),
                resultSet.getObject("team_id", UUID.class),
                resultSet.getString("team_name"),
                resultSet.getString("team_tag"),
                resultSet.getString("team_slug"),
                resultSet.getObject("captain_profile_id", UUID.class),
                resultSet.getString("captain_nickname"),
                TournamentRegistrationStatus.fromDatabaseValue(resultSet.getString("status")),
                resultSet.getString("message"),
                resultSet.getObject("reviewed_by", UUID.class),
                resultSet.getString("reviewed_by_nickname"),
                resultSet.getObject("reviewed_at", OffsetDateTime.class),
                resultSet.getObject("seed_number", Integer.class),
                resultSet.getObject("checked_in_at", OffsetDateTime.class),
                resultSet.getString("contact_email"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class));
    }

    private TournamentRegistrationMember mapRegistrationMember(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TournamentRegistrationMember(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("registration_id", UUID.class),
                resultSet.getObject("profile_id", UUID.class),
                resultSet.getString("nickname"),
                resultSet.getString("display_name"),
                resultSet.getString("avatar_url"),
                resultSet.getObject("team_member_id", UUID.class),
                resultSet.getObject("manual_player_id", UUID.class),
                resultSet.getString("manual_note"),
                resultSet.getString("member_role") == null
                        ? null
                        : TeamMemberRole.fromDatabaseValue(resultSet.getString("member_role")),
                resultSet.getBoolean("is_starter"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class));
    }
}
