package si.um.feri.dotaops.backend.team.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import si.um.feri.dotaops.backend.team.domain.Team;

@Repository
public class TeamRepository {

    private final JdbcTemplate jdbcTemplate;

    public TeamRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Team> findTeams(String search, int size, long offset) {
        String normalizedSearch = normalizeSearch(search);

        return jdbcTemplate.query(
                selectTeamSql() + """
                where t.disbanded_at is null
                  and (
                  cast(? as text) is null
                  or t.name ilike '%' || cast(? as text) || '%'
                  or t.tag ilike '%' || cast(? as text) || '%'
                  or t.slug ilike '%' || cast(? as text) || '%'
                )
                order by t.created_at desc, t.id desc
                limit ? offset ?
                """,
                this::mapTeam,
                normalizedSearch,
                normalizedSearch,
                normalizedSearch,
                normalizedSearch,
                size,
                offset);
    }

    public long countTeams(String search) {
        String normalizedSearch = normalizeSearch(search);
        Long count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from public.teams t
                where t.disbanded_at is null
                  and (
                  cast(? as text) is null
                  or t.name ilike '%' || cast(? as text) || '%'
                  or t.tag ilike '%' || cast(? as text) || '%'
                  or t.slug ilike '%' || cast(? as text) || '%'
                )
                """,
                Long.class,
                normalizedSearch,
                normalizedSearch,
                normalizedSearch,
                normalizedSearch);

        return count == null ? 0 : count;
    }

    public Optional<Team> findById(UUID teamId) {
        return jdbcTemplate.query(
                        selectTeamSql() + "where t.id = ? and t.disbanded_at is null limit 1",
                        this::mapTeam,
                        teamId)
                .stream()
                .findFirst();
    }

    public Optional<Team> findByIdForUpdate(UUID teamId) {
        return jdbcTemplate.query(
                        selectTeamSql() + "where t.id = ? and t.disbanded_at is null limit 1 for update of t",
                        this::mapTeam,
                        teamId)
                .stream()
                .findFirst();
    }

    public Optional<Team> findBySlug(String slug) {
        return jdbcTemplate.query(
                        selectTeamSql() + "where t.slug = ? and t.disbanded_at is null limit 1",
                        this::mapTeam,
                        slug)
                .stream()
                .findFirst();
    }

    public Optional<Team> findCurrentTeamForProfile(UUID profileId) {
        return jdbcTemplate.query(
                        selectTeamSql() + """
                        where t.disbanded_at is null
                          and (
                            t.captain_profile_id = ?
                            or exists (
                              select 1
                              from public.team_members tm
                              where tm.team_id = t.id
                                and tm.profile_id = ?
                                and tm.is_active = true
                            )
                          )
                        order by
                          case when t.captain_profile_id = ? then 0 else 1 end,
                          t.updated_at desc nulls last,
                          t.created_at desc,
                          t.id desc
                        limit 1
                        """,
                        this::mapTeam,
                        profileId,
                        profileId,
                        profileId)
                .stream()
                .findFirst();
    }

    public boolean existsCurrentTeamForProfileExcluding(UUID profileId, UUID excludedTeamId) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                select exists (
                  select 1
                  from public.teams t
                  where t.disbanded_at is null
                    and (cast(? as uuid) is null or t.id <> ?)
                    and (
                      t.captain_profile_id = ?
                      or exists (
                        select 1
                        from public.team_members tm
                        where tm.team_id = t.id
                          and tm.profile_id = ?
                          and tm.is_active = true
                      )
                    )
                )
                """,
                Boolean.class,
                excludedTeamId,
                excludedTeamId,
                profileId,
                profileId);

        return Boolean.TRUE.equals(exists);
    }

    public Team create(CreateTeamCommand command) {
        return jdbcTemplate.queryForObject(
                """
                insert into public.teams (
                  name,
                  tag,
                  slug,
                  captain_profile_id,
                  region,
                  logo_url,
                  description,
                  created_by
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                returning
                  id,
                  name,
                  tag,
                  slug,
                  captain_profile_id,
                  (
                    select p.nickname
                    from public.profiles p
                    where p.id = captain_profile_id
                  ) as captain_nickname,
                  region,
                  logo_url,
                  banner_url,
                  description,
                  created_by,
                  created_at,
                  updated_at
                """,
                this::mapTeam,
                command.name(),
                command.tag(),
                command.slug(),
                command.captainProfileId(),
                command.region(),
                command.logoUrl(),
                command.description(),
                command.createdBy());
    }

    public Optional<Team> update(UUID teamId, UpdateTeamCommand command) {
        return jdbcTemplate.query(
                        """
                        update public.teams
                        set
                          name = case when ? then ? else name end,
                          tag = case when ? then ? else tag end,
                          slug = case when ? then ? else slug end,
                          region = case when ? then ? else region end,
                          logo_url = case when ? then ? else logo_url end,
                          description = case when ? then ? else description end,
                          updated_at = now()
                        where id = ?
                          and disbanded_at is null
                        returning
                          id,
                          name,
                          tag,
                          slug,
                          captain_profile_id,
                          (
                            select p.nickname
                            from public.profiles p
                            where p.id = captain_profile_id
                          ) as captain_nickname,
                          region,
                          logo_url,
                          banner_url,
                          description,
                          created_by,
                          created_at,
                          updated_at
                        """,
                        this::mapTeam,
                        command.namePresent(),
                        command.name(),
                        command.tagPresent(),
                        command.tag(),
                        command.slugPresent(),
                        command.slug(),
                        command.regionPresent(),
                        command.region(),
                        command.logoUrlPresent(),
                        command.logoUrl(),
                        command.descriptionPresent(),
                        command.description(),
                        teamId)
                .stream()
                .findFirst();
    }

    public Optional<Team> updateLogoUrl(UUID teamId, String logoUrl) {
        return jdbcTemplate.query(
                        """
                        update public.teams
                        set
                          logo_url = ?,
                          updated_at = now()
                        where id = ?
                          and disbanded_at is null
                        returning
                          id,
                          name,
                          tag,
                          slug,
                          captain_profile_id,
                          (
                            select p.nickname
                            from public.profiles p
                            where p.id = captain_profile_id
                          ) as captain_nickname,
                          region,
                          logo_url,
                          banner_url,
                          description,
                          created_by,
                          created_at,
                          updated_at
                        """,
                        this::mapTeam,
                        logoUrl,
                        teamId)
                .stream()
                .findFirst();
    }

    public Optional<Team> updateBannerUrl(UUID teamId, String bannerUrl) {
        return jdbcTemplate.query(
                        """
                        update public.teams
                        set
                          banner_url = ?,
                          updated_at = now()
                        where id = ?
                          and disbanded_at is null
                        returning
                          id,
                          name,
                          tag,
                          slug,
                          captain_profile_id,
                          (
                            select p.nickname
                            from public.profiles p
                            where p.id = captain_profile_id
                          ) as captain_nickname,
                          region,
                          logo_url,
                          banner_url,
                          description,
                          created_by,
                          created_at,
                          updated_at
                        """,
                        this::mapTeam,
                        bannerUrl,
                        teamId)
                .stream()
                .findFirst();
    }

    public Optional<Team> transferOwnership(UUID teamId, UUID newCaptainProfileId) {
        return jdbcTemplate.query(
                        """
                        update public.teams
                        set
                          captain_profile_id = ?,
                          updated_at = now()
                        where id = ?
                          and disbanded_at is null
                        returning
                          id,
                          name,
                          tag,
                          slug,
                          captain_profile_id,
                          (
                            select p.nickname
                            from public.profiles p
                            where p.id = captain_profile_id
                          ) as captain_nickname,
                          region,
                          logo_url,
                          banner_url,
                          description,
                          created_by,
                          created_at,
                          updated_at
                        """,
                        this::mapTeam,
                        newCaptainProfileId,
                        teamId)
                .stream()
                .findFirst();
    }

    public Optional<OffsetDateTime> disband(UUID teamId) {
        return jdbcTemplate.query(
                        """
                        update public.teams
                        set
                          disbanded_at = now(),
                          updated_at = now()
                        where id = ?
                          and disbanded_at is null
                        returning disbanded_at
                        """,
                        (resultSet, rowNumber) -> resultSet.getObject("disbanded_at", OffsetDateTime.class),
                        teamId)
                .stream()
                .findFirst();
    }

    private String selectTeamSql() {
        return """
                select
                  t.id,
                  t.name,
                  t.tag,
                  t.slug,
                  t.captain_profile_id,
                  p.nickname as captain_nickname,
                  t.region,
                  t.logo_url,
                  t.banner_url,
                  t.description,
                  t.created_by,
                  t.created_at,
                  t.updated_at
                from public.teams t
                left join public.profiles p on p.id = t.captain_profile_id
                """;
    }

    private Team mapTeam(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Team(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("tag"),
                resultSet.getString("slug"),
                resultSet.getObject("captain_profile_id", UUID.class),
                resultSet.getString("captain_nickname"),
                resultSet.getString("region"),
                resultSet.getString("logo_url"),
                resultSet.getString("banner_url"),
                resultSet.getString("description"),
                resultSet.getObject("created_by", UUID.class),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class));
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }

        return search.trim();
    }
}
