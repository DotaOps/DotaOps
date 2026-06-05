package si.um.feri.dotaops.backend.analytics.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import si.um.feri.dotaops.backend.analytics.domain.AnalyticsFilters;

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

    public List<PlayerComparisonCandidate> findAnalyzedPlayerComparisonCandidates(
            UUID excludedProfileId,
            String query,
            AnalyticsFilters filters,
            boolean publicOnly,
            boolean exact,
            int limit
    ) {
        PlayerCandidateQueryParts queryParts = playerAnalyticsFilters(filters);
        String normalizedQuery = query.toLowerCase();
        String queryPattern = "%" + escapedLikePattern(normalizedQuery) + "%";
        List<Object> parameters = new ArrayList<>();
        parameters.add(excludedProfileId != null);
        parameters.add(excludedProfileId);
        addNameParameters(parameters, normalizedQuery, queryPattern, exact);
        parameters.addAll(queryParts.parameters());
        parameters.add(normalizedQuery);
        parameters.add(normalizedQuery);
        parameters.add(normalizedQuery);
        parameters.add(limit);

        return jdbcTemplate.query(
                """
                select
                  p.id as profile_id,
                  coalesce(p.display_name, p.nickname, 'Unknown player') as display_name,
                  p.nickname,
                  p.avatar_url,
                  p.opendota_account_id,
                  case
                    when count(distinct mp.team_id) = 1 then min(mp.team_id::text)::uuid
                    else null::uuid
                  end as team_id,
                  string_agg(distinct tm.name, ', ' order by tm.name) as team_name,
                  count(*)::integer as analytics_games_count
                from public.match_players mp
                left join public.match_games mg on mg.id = mp.match_game_id
                join public.matches m on m.id = coalesce(mg.match_id, mp.match_id)
                join public.tournaments t on t.id = m.tournament_id
                join public.profiles p on p.id = mp.profile_id
                left join public.teams tm on tm.id = mp.team_id
                """ + "where " + tournamentVisibilityCondition(publicOnly) + "\n" + """
                  and mp.profile_id is not null
                  and p.role = 'player'::public.dotaops_user_role
                  and (? = false or p.id <> ?)
                  """ + "and " + playerNameCondition(exact) + "\n"
                  + queryParts.sql() + """
                group by p.id, p.display_name, p.nickname, p.avatar_url, p.opendota_account_id
                order by
                  case
                    when lower(coalesce(p.display_name, '')) = ?
                      or lower(coalesce(p.nickname, '')) = ?
                      or coalesce(p.opendota_account_id::text, '') = ? then 0
                    else 1
                  end,
                  analytics_games_count desc,
                  lower(coalesce(p.display_name, p.nickname, '')) asc,
                  p.id asc
                limit ?
                """,
                this::mapPlayerComparisonCandidate,
                parameters.toArray());
    }

    public List<PlayerComparisonCandidate> findActiveTeamPlayerComparisonCandidates(
            UUID teamId,
            UUID excludedProfileId,
            String query,
            boolean exact,
            int limit
    ) {
        String normalizedQuery = query.toLowerCase();
        String queryPattern = "%" + escapedLikePattern(normalizedQuery) + "%";
        List<Object> parameters = new ArrayList<>();
        parameters.add(teamId);
        parameters.add(excludedProfileId != null);
        parameters.add(excludedProfileId);
        addNameParameters(parameters, normalizedQuery, queryPattern, exact);
        parameters.add(normalizedQuery);
        parameters.add(normalizedQuery);
        parameters.add(normalizedQuery);
        parameters.add(limit);

        return jdbcTemplate.query(
                """
                select
                  p.id as profile_id,
                  coalesce(p.display_name, p.nickname, 'Unknown player') as display_name,
                  p.nickname,
                  p.avatar_url,
                  p.opendota_account_id,
                  t.id as team_id,
                  t.name as team_name,
                  (
                    select count(*)::integer
                    from public.match_players analytics_mp
                    where analytics_mp.profile_id = p.id
                  ) as analytics_games_count
                from public.team_members tm
                join public.profiles p on p.id = tm.profile_id
                join public.teams t on t.id = tm.team_id
                where tm.team_id = ?
                  and tm.is_active = true
                  and t.disbanded_at is null
                  and p.role = 'player'::public.dotaops_user_role
                  and (? = false or p.id <> ?)
                  """ + "and " + playerNameCondition(exact) + """
                order by
                  case
                    when lower(coalesce(p.display_name, '')) = ?
                      or lower(coalesce(p.nickname, '')) = ?
                      or coalesce(p.opendota_account_id::text, '') = ? then 0
                    else 1
                  end,
                  analytics_games_count desc,
                  tm.joined_at asc,
                  lower(coalesce(p.display_name, p.nickname, '')) asc,
                  p.id asc
                limit ?
                """,
                this::mapPlayerComparisonCandidate,
                parameters.toArray());
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

    private PlayerComparisonCandidate mapPlayerComparisonCandidate(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new PlayerComparisonCandidate(
                resultSet.getObject("profile_id", UUID.class),
                resultSet.getString("display_name"),
                resultSet.getString("nickname"),
                resultSet.getObject("team_id", UUID.class),
                resultSet.getString("team_name"),
                resultSet.getString("avatar_url"),
                resultSet.getObject("opendota_account_id", Long.class),
                resultSet.getInt("analytics_games_count"));
    }

    private PlayerCandidateQueryParts playerAnalyticsFilters(AnalyticsFilters filters) {
        List<String> clauses = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        String analyticsTimestamp = analyticsTimestampExpression();

        if (filters.tournamentId() != null) {
            clauses.add("m.tournament_id = ?");
            parameters.add(filters.tournamentId());
        }
        if (filters.teamId() != null) {
            clauses.add("mp.team_id = ?");
            parameters.add(filters.teamId());
        }
        if (filters.heroId() != null) {
            clauses.add("mp.hero_id = ?");
            parameters.add(filters.heroId());
        }
        if (filters.from() != null) {
            clauses.add(analyticsTimestamp + " >= ?");
            parameters.add(filters.from());
        }
        if (filters.to() != null) {
            clauses.add(analyticsTimestamp + " < ?");
            parameters.add(filters.to());
        }

        if (clauses.isEmpty()) {
            return new PlayerCandidateQueryParts("", parameters);
        }

        return new PlayerCandidateQueryParts("  and " + String.join("\n  and ", clauses) + "\n", parameters);
    }

    private String analyticsTimestampExpression() {
        return "coalesce(mg.started_at, mg.finished_at, m.started_at, m.finished_at, "
                + "m.scheduled_at, mg.created_at, m.created_at)";
    }

    private String tournamentVisibilityCondition(boolean publicOnly) {
        return publicOnly ? "t.is_public = true" : "true";
    }

    private String playerNameCondition(boolean exact) {
        if (exact) {
            return "(lower(coalesce(p.display_name, '')) = ? "
                    + "or lower(coalesce(p.nickname, '')) = ? "
                    + "or coalesce(p.opendota_account_id::text, '') = ?)";
        }

        return "(lower(coalesce(p.display_name, '')) like ? escape '\\' "
                + "or lower(coalesce(p.nickname, '')) like ? escape '\\' "
                + "or coalesce(p.opendota_account_id::text, '') like ? escape '\\')";
    }

    private void addNameParameters(
            List<Object> parameters,
            String normalizedQuery,
            String queryPattern,
            boolean exact
    ) {
        if (exact) {
            parameters.add(normalizedQuery);
            parameters.add(normalizedQuery);
            parameters.add(normalizedQuery);
            return;
        }

        parameters.add(queryPattern);
        parameters.add(queryPattern);
        parameters.add(queryPattern);
    }

    private String escapedLikePattern(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
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

    public record PlayerComparisonCandidate(
            UUID profileId,
            String displayName,
            String nickname,
            UUID teamId,
            String teamName,
            String avatarUrl,
            Long opendotaAccountId,
            int analyticsGamesCount,
            boolean hasAnalyticsData,
            String label
    ) {

        public PlayerComparisonCandidate(
                UUID profileId,
                String displayName,
                String nickname,
                UUID teamId,
                String teamName
        ) {
            this(profileId, displayName, nickname, teamId, teamName, null, null, 0);
        }

        public PlayerComparisonCandidate(
                UUID profileId,
                String displayName,
                String nickname,
                UUID teamId,
                String teamName,
                String avatarUrl,
                Long opendotaAccountId,
                int analyticsGamesCount
        ) {
            this(
                    profileId,
                    displayName,
                    nickname,
                    teamId,
                    teamName,
                    avatarUrl,
                    opendotaAccountId,
                    analyticsGamesCount,
                    analyticsGamesCount > 0,
                    analyticsGamesCount > 0
                            ? analyticsGamesCount + " imported analytics matches"
                            : "No imported matches yet");
        }
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

    private record PlayerCandidateQueryParts(
            String sql,
            List<Object> parameters
    ) {
    }
}
