package si.um.feri.dotaops.backend.analytics.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalyticsLookupRepository {

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsLookupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TournamentLookup> findManageableTournaments(UUID profileId, boolean admin, int limit) {
        return jdbcTemplate.query(
                """
                select
                  t.id,
                  t.title,
                  t.status::text as status
                from public.tournaments t
                where ? = true
                   or t.organizer_profile_id = ?
                   or exists (
                     select 1
                     from public.tournament_staff ts
                     where ts.tournament_id = t.id
                       and ts.profile_id = ?
                       and ts.staff_role in ('owner', 'organizer')
                   )
                order by t.updated_at desc, t.created_at desc, t.title asc
                limit ?
                """,
                (resultSet, rowNumber) -> new TournamentLookup(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("title"),
                        resultSet.getString("status")),
                admin,
                profileId,
                profileId,
                limit);
    }

    public List<TeamLookup> findCurrentTeams(UUID profileId, int limit) {
        return jdbcTemplate.query(
                """
                select
                  t.id,
                  t.name,
                  t.tag
                from public.teams t
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
                  t.name asc
                limit ?
                """,
                this::mapTeamLookup,
                profileId,
                profileId,
                profileId,
                limit);
    }

    public List<PlayerLookup> findActiveTeamPlayers(UUID teamId, int limit) {
        return jdbcTemplate.query(
                """
                select
                  p.id as profile_id,
                  coalesce(p.display_name, p.nickname, 'Unknown player') as display_name,
                  p.nickname,
                  t.id as team_id,
                  t.name as team_name
                from public.team_members tm
                join public.profiles p on p.id = tm.profile_id
                join public.teams t on t.id = tm.team_id
                where tm.team_id = ?
                  and tm.is_active = true
                  and t.disbanded_at is null
                order by tm.joined_at asc, p.display_name asc nulls last, p.nickname asc nulls last
                limit ?
                """,
                this::mapPlayerLookup,
                teamId,
                limit);
    }

    public List<HeroLookup> findHeroes(int limit) {
        return jdbcTemplate.query(
                """
                select
                  id,
                  dota_hero_id,
                  name,
                  localized_name,
                  image_url,
                  icon_url
                from public.heroes
                order by localized_name asc, dota_hero_id asc
                limit ?
                """,
                (resultSet, rowNumber) -> new HeroLookup(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("dota_hero_id", Integer.class),
                        resultSet.getString("name"),
                        resultSet.getString("localized_name"),
                        resultSet.getString("image_url"),
                        resultSet.getString("icon_url")),
                limit);
    }

    public boolean isActiveTeamMember(UUID teamId, UUID profileId) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                select exists (
                  select 1
                  from public.teams t
                  where t.id = ?
                    and t.disbanded_at is null
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
                teamId,
                profileId,
                profileId);

        return Boolean.TRUE.equals(exists);
    }

    public boolean teamsShareActiveMembership(UUID currentProfileId, UUID firstProfileId, UUID secondProfileId) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                select exists (
                  select 1
                  from public.teams t
                  where t.disbanded_at is null
                    and (
                      t.captain_profile_id = ?
                      or exists (
                        select 1
                        from public.team_members current_member
                        where current_member.team_id = t.id
                          and current_member.profile_id = ?
                          and current_member.is_active = true
                      )
                    )
                    and (
                      t.captain_profile_id = ?
                      or exists (
                        select 1
                        from public.team_members first_member
                        where first_member.team_id = t.id
                          and first_member.profile_id = ?
                          and first_member.is_active = true
                      )
                    )
                    and (
                      t.captain_profile_id = ?
                      or exists (
                        select 1
                        from public.team_members second_member
                        where second_member.team_id = t.id
                          and second_member.profile_id = ?
                          and second_member.is_active = true
                      )
                    )
                )
                """,
                Boolean.class,
                currentProfileId,
                currentProfileId,
                firstProfileId,
                firstProfileId,
                secondProfileId,
                secondProfileId);

        return Boolean.TRUE.equals(exists);
    }

    public boolean teamAppearsInManageableTournament(UUID teamId, UUID profileId, boolean admin) {
        if (admin) {
            return true;
        }

        Boolean exists = jdbcTemplate.queryForObject(
                """
                select exists (
                  select 1
                  from public.tournaments t
                  where (
                      t.organizer_profile_id = ?
                      or exists (
                        select 1
                        from public.tournament_staff ts
                        where ts.tournament_id = t.id
                          and ts.profile_id = ?
                          and ts.staff_role in ('owner', 'organizer')
                      )
                    )
                    and (
                      exists (
                        select 1
                        from public.tournament_registrations tr
                        where tr.tournament_id = t.id
                          and tr.team_id = ?
                      )
                      or exists (
                        select 1
                        from public.matches m
                        where m.tournament_id = t.id
                          and (m.team_a_id = ? or m.team_b_id = ?)
                      )
                      or exists (
                        select 1
                        from public.matches m
                        join public.match_players mp on mp.match_id = m.id
                        where m.tournament_id = t.id
                          and mp.team_id = ?
                      )
                      or exists (
                        select 1
                        from public.matches m
                        join public.match_games mg on mg.match_id = m.id
                        join public.match_players mp on mp.match_game_id = mg.id
                        where m.tournament_id = t.id
                          and mp.team_id = ?
                      )
                    )
                )
                """,
                Boolean.class,
                profileId,
                profileId,
                teamId,
                teamId,
                teamId,
                teamId,
                teamId);

        return Boolean.TRUE.equals(exists);
    }

    public boolean profileAppearsInManageableTournament(UUID targetProfileId, UUID profileId, boolean admin) {
        if (admin) {
            return true;
        }

        Boolean exists = jdbcTemplate.queryForObject(
                """
                select exists (
                  select 1
                  from public.tournaments t
                  where (
                      t.organizer_profile_id = ?
                      or exists (
                        select 1
                        from public.tournament_staff ts
                        where ts.tournament_id = t.id
                          and ts.profile_id = ?
                          and ts.staff_role in ('owner', 'organizer')
                      )
                    )
                    and (
                      exists (
                        select 1
                        from public.tournament_registrations tr
                        join public.tournament_registration_members trm on trm.registration_id = tr.id
                        where tr.tournament_id = t.id
                          and trm.profile_id = ?
                      )
                      or exists (
                        select 1
                        from public.matches m
                        join public.match_players mp on mp.match_id = m.id
                        where m.tournament_id = t.id
                          and mp.profile_id = ?
                      )
                      or exists (
                        select 1
                        from public.matches m
                        join public.match_games mg on mg.match_id = m.id
                        join public.match_players mp on mp.match_game_id = mg.id
                        where m.tournament_id = t.id
                          and mp.profile_id = ?
                      )
                    )
                )
                """,
                Boolean.class,
                profileId,
                profileId,
                targetProfileId,
                targetProfileId,
                targetProfileId);

        return Boolean.TRUE.equals(exists);
    }

    private TeamLookup mapTeamLookup(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TeamLookup(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("tag"));
    }

    private PlayerLookup mapPlayerLookup(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PlayerLookup(
                resultSet.getObject("profile_id", UUID.class),
                resultSet.getString("display_name"),
                resultSet.getString("nickname"),
                resultSet.getObject("team_id", UUID.class),
                resultSet.getString("team_name"));
    }

    public record TournamentLookup(
            UUID tournamentId,
            String title,
            String status
    ) {
    }

    public record TeamLookup(
            UUID teamId,
            String name,
            String tag
    ) {
    }

    public record PlayerLookup(
            UUID profileId,
            String displayName,
            String nickname,
            UUID teamId,
            String teamName
    ) {
    }

    public record HeroLookup(
            UUID heroId,
            Integer dotaHeroId,
            String name,
            String localizedName,
            String imageUrl,
            String iconUrl
    ) {
    }
}
