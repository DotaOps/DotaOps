package si.um.feri.dotaops.backend.analytics.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import si.um.feri.dotaops.backend.analytics.domain.AnalyticsFilters;
import si.um.feri.dotaops.backend.analytics.domain.AnalyticsMatchHistory;
import si.um.feri.dotaops.backend.analytics.domain.HeroMetrics;
import si.um.feri.dotaops.backend.analytics.domain.PickedHeroMetrics;
import si.um.feri.dotaops.backend.analytics.domain.PlayerComparisonHeadlineMetrics;
import si.um.feri.dotaops.backend.analytics.domain.PlayerComparisonMatch;
import si.um.feri.dotaops.backend.analytics.domain.PlayerHeroPerformance;
import si.um.feri.dotaops.backend.analytics.domain.PlayerMetrics;
import si.um.feri.dotaops.backend.analytics.domain.PlayerProgressPoint;
import si.um.feri.dotaops.backend.analytics.domain.TeamMetrics;
import si.um.feri.dotaops.backend.analytics.domain.TournamentMetrics;

@Repository
public class AnalyticsRepository {

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PlayerMetrics> findPlayerMetrics(AnalyticsFilters filters) {
        return findPlayerMetrics(filters, true);
    }

    public List<PlayerMetrics> findProtectedPlayerMetrics(AnalyticsFilters filters) {
        return findPlayerMetrics(filters, false);
    }

    public Optional<PlayerMetrics> findPlayerAggregateMetrics(
            UUID profileId,
            AnalyticsFilters filters,
            boolean publicOnly
    ) {
        AnalyticsFilters scopedFilters = filters.withProfileId(profileId);
        QueryParts queryParts = filteredWhere(scopedFilters, "mp", "m");
        List<Object> parameters = new ArrayList<>(queryParts.parameters());

        return jdbcTemplate.query(
                        """
                        select
                          p.id as profile_id,
                          coalesce(p.display_name, p.nickname, 'Unknown player') as display_name,
                          null::uuid as team_id,
                          null::text as team_name,
                          null::uuid as tournament_id,
                          null::text as tournament_name,
                          count(*)::integer as games_played,
                          count(*) filter (where mp.is_winner is true)::integer as wins,
                          count(*) filter (where mp.is_winner is false)::integer as losses,
                          round(((count(*) filter (where mp.is_winner is true))::numeric / greatest(count(*), 1)) * 100, 2)
                            as win_rate,
                          coalesce(sum(mp.kills), 0)::integer as kills,
                          coalesce(sum(mp.deaths), 0)::integer as deaths,
                          coalesce(sum(mp.assists), 0)::integer as assists,
                          round(avg(mp.kills), 2) as avg_kills,
                          round(avg(mp.deaths), 2) as avg_deaths,
                          round(avg(mp.assists), 2) as avg_assists,
                          round((coalesce(sum(mp.kills), 0) + coalesce(sum(mp.assists), 0))::numeric
                            / greatest(coalesce(sum(mp.deaths), 0), 1), 2) as kda,
                          round(avg(mp.gold_per_min), 2) as avg_gpm,
                          round(avg(mp.xp_per_min), 2) as avg_xpm,
                          round(avg(mp.hero_damage), 2) as avg_hero_damage
                        from public.match_players mp
                        left join public.match_games mg on mg.id = mp.match_game_id
                        join public.matches m on m.id = coalesce(mg.match_id, mp.match_id)
                        join public.tournaments t on t.id = m.tournament_id
                        join public.profiles p on p.id = mp.profile_id
                        where """ + tournamentVisibilityCondition(publicOnly) + """
                          and mp.profile_id is not null
                        """ + queryParts.sql() + """
                        group by p.id, p.display_name, p.nickname
                        """,
                        this::mapPlayerMetrics,
                        parameters.toArray())
                .stream()
                .findFirst();
    }

    public Optional<PlayerComparisonHeadlineMetrics> findPlayerComparisonHeadlineMetrics(
            UUID profileId,
            AnalyticsFilters filters,
            boolean publicOnly
    ) {
        AnalyticsFilters scopedFilters = filters.withProfileId(profileId);
        QueryParts queryParts = filteredWhere(scopedFilters, "mp", "m");
        List<Object> parameters = new ArrayList<>(queryParts.parameters());

        return jdbcTemplate.query(
                        """
                        select
                          p.id as profile_id,
                          coalesce(p.display_name, p.nickname, 'Unknown player') as display_name,
                          count(*)::integer as games_played,
                          count(*) filter (where mp.is_winner is true)::integer as wins,
                          count(*) filter (where mp.is_winner is false)::integer as losses,
                          round(((count(*) filter (where mp.is_winner is true))::numeric / greatest(count(*), 1)) * 100, 2)
                            as win_rate,
                          round((coalesce(sum(mp.kills), 0) + coalesce(sum(mp.assists), 0))::numeric
                            / greatest(coalesce(sum(mp.deaths), 0), 1), 2) as kda,
                          round(avg(mp.kills), 2) as avg_kills,
                          round(avg(mp.deaths), 2) as avg_deaths,
                          round(avg(mp.assists), 2) as avg_assists,
                          round(avg(mp.gold_per_min), 2) as avg_gpm,
                          round(avg(mp.xp_per_min), 2) as avg_xpm,
                          round(avg(mp.last_hits), 2) as avg_last_hits,
                          round(avg(mp.denies), 2) as avg_denies,
                          round(avg(mp.net_worth), 2) as avg_net_worth,
                          round(avg(mp.hero_damage), 2) as avg_hero_damage,
                          round(avg(mp.tower_damage), 2) as avg_tower_damage,
                          round(avg(mp.hero_healing), 2) as avg_hero_healing
                        from public.match_players mp
                        left join public.match_games mg on mg.id = mp.match_game_id
                        join public.matches m on m.id = coalesce(mg.match_id, mp.match_id)
                        join public.tournaments t on t.id = m.tournament_id
                        join public.profiles p on p.id = mp.profile_id
                        where """ + tournamentVisibilityCondition(publicOnly) + """
                          and mp.profile_id is not null
                        """ + queryParts.sql() + """
                        group by p.id, p.display_name, p.nickname
                        """,
                        this::mapPlayerComparisonHeadlineMetrics,
                        parameters.toArray())
                .stream()
                .findFirst();
    }

    private List<PlayerMetrics> findPlayerMetrics(AnalyticsFilters filters, boolean publicOnly) {
        QueryParts queryParts = filteredWhere(filters, "mp", "m");
        List<Object> parameters = new ArrayList<>(queryParts.parameters());
        parameters.add(filters.limit());

        return jdbcTemplate.query(
                """
                select
                  p.id as profile_id,
                  coalesce(p.display_name, p.nickname, 'Unknown player') as display_name,
                  mp.team_id,
                  tm.name as team_name,
                  m.tournament_id,
                  t.title as tournament_name,
                  count(*)::integer as games_played,
                  count(*) filter (where mp.is_winner is true)::integer as wins,
                  count(*) filter (where mp.is_winner is false)::integer as losses,
                  round(((count(*) filter (where mp.is_winner is true))::numeric / greatest(count(*), 1)) * 100, 2)
                    as win_rate,
                  coalesce(sum(mp.kills), 0)::integer as kills,
                  coalesce(sum(mp.deaths), 0)::integer as deaths,
                  coalesce(sum(mp.assists), 0)::integer as assists,
                  round(avg(mp.kills), 2) as avg_kills,
                  round(avg(mp.deaths), 2) as avg_deaths,
                  round(avg(mp.assists), 2) as avg_assists,
                  round((coalesce(sum(mp.kills), 0) + coalesce(sum(mp.assists), 0))::numeric
                    / greatest(coalesce(sum(mp.deaths), 0), 1), 2) as kda,
                  round(avg(mp.gold_per_min), 2) as avg_gpm,
                  round(avg(mp.xp_per_min), 2) as avg_xpm,
                  round(avg(mp.hero_damage), 2) as avg_hero_damage
                from public.match_players mp
                left join public.match_games mg on mg.id = mp.match_game_id
                join public.matches m on m.id = coalesce(mg.match_id, mp.match_id)
                join public.tournaments t on t.id = m.tournament_id
                join public.profiles p on p.id = mp.profile_id
                left join public.teams tm on tm.id = mp.team_id
                where """ + tournamentVisibilityCondition(publicOnly) + """
                  and mp.profile_id is not null
                """ + queryParts.sql() + """
                group by p.id, p.display_name, p.nickname, mp.team_id, tm.name, m.tournament_id, t.title
                order by games_played desc, kda desc, p.display_name asc nulls last
                limit ?
                """,
                this::mapPlayerMetrics,
                parameters.toArray());
    }

    public List<TeamMetrics> findTeamMetrics(AnalyticsFilters filters) {
        return findTeamMetrics(filters, true);
    }

    public List<TeamMetrics> findProtectedTeamMetrics(AnalyticsFilters filters) {
        return findTeamMetrics(filters, false);
    }

    public Optional<TeamMetrics> findTeamAggregateMetrics(
            UUID teamId,
            AnalyticsFilters filters,
            boolean publicOnly
    ) {
        AnalyticsFilters scopedFilters = filters.withTeamId(teamId);
        QueryParts queryParts = filteredWhere(scopedFilters, "mp", "m");
        List<Object> parameters = new ArrayList<>(queryParts.parameters());

        return jdbcTemplate.query(
                        """
                        select
                          tm.id as team_id,
                          tm.name as team_name,
                          null::uuid as tournament_id,
                          null::text as tournament_name,
                          count(distinct coalesce(mp.match_game_id::text, mp.match_id::text))::integer as games_played,
                          count(distinct coalesce(mp.match_game_id::text, mp.match_id::text))
                            filter (where mp.is_winner is true)::integer as wins,
                          count(distinct coalesce(mp.match_game_id::text, mp.match_id::text))
                            filter (where mp.is_winner is false)::integer as losses,
                          round(((count(distinct coalesce(mp.match_game_id::text, mp.match_id::text))
                            filter (where mp.is_winner is true))::numeric
                            / greatest(count(distinct coalesce(mp.match_game_id::text, mp.match_id::text)), 1)) * 100, 2)
                            as win_rate,
                          coalesce(sum(mp.kills), 0)::integer as total_kills,
                          coalesce(sum(mp.deaths), 0)::integer as total_deaths,
                          coalesce(sum(mp.assists), 0)::integer as total_assists,
                          round(coalesce(sum(mp.kills), 0)::numeric
                            / greatest(count(distinct coalesce(mp.match_game_id::text, mp.match_id::text)), 1), 2) as avg_kills,
                          round(coalesce(sum(mp.deaths), 0)::numeric
                            / greatest(count(distinct coalesce(mp.match_game_id::text, mp.match_id::text)), 1), 2) as avg_deaths,
                          round(coalesce(sum(mp.assists), 0)::numeric
                            / greatest(count(distinct coalesce(mp.match_game_id::text, mp.match_id::text)), 1), 2) as avg_assists,
                          round((coalesce(sum(mp.kills), 0) + coalesce(sum(mp.assists), 0))::numeric
                            / greatest(coalesce(sum(mp.deaths), 0), 1), 2) as avg_kda,
                          round(avg(mp.gold_per_min), 2) as avg_gpm,
                          round(avg(mp.xp_per_min), 2) as avg_xpm,
                          round(avg(mp.hero_damage), 2) as avg_hero_damage
                        from public.match_players mp
                        left join public.match_games mg on mg.id = mp.match_game_id
                        join public.matches m on m.id = coalesce(mg.match_id, mp.match_id)
                        join public.tournaments t on t.id = m.tournament_id
                        join public.teams tm on tm.id = mp.team_id
                        where """ + tournamentVisibilityCondition(publicOnly) + """
                          and mp.team_id is not null
                        """ + queryParts.sql() + """
                        group by tm.id, tm.name
                        """,
                        this::mapTeamMetrics,
                        parameters.toArray())
                .stream()
                .findFirst();
    }

    private List<TeamMetrics> findTeamMetrics(AnalyticsFilters filters, boolean publicOnly) {
        QueryParts queryParts = filteredWhere(filters, "mp", "m");
        List<Object> parameters = new ArrayList<>(queryParts.parameters());
        parameters.add(filters.limit());

        return jdbcTemplate.query(
                """
                select
                  tm.id as team_id,
                  tm.name as team_name,
                  m.tournament_id,
                  t.title as tournament_name,
                  count(distinct coalesce(mp.match_game_id::text, mp.match_id::text))::integer as games_played,
                  count(distinct coalesce(mp.match_game_id::text, mp.match_id::text))
                    filter (where mp.is_winner is true)::integer as wins,
                  count(distinct coalesce(mp.match_game_id::text, mp.match_id::text))
                    filter (where mp.is_winner is false)::integer as losses,
                  round(((count(distinct coalesce(mp.match_game_id::text, mp.match_id::text))
                    filter (where mp.is_winner is true))::numeric
                    / greatest(count(distinct coalesce(mp.match_game_id::text, mp.match_id::text)), 1)) * 100, 2)
                    as win_rate,
                  coalesce(sum(mp.kills), 0)::integer as total_kills,
                  coalesce(sum(mp.deaths), 0)::integer as total_deaths,
                  coalesce(sum(mp.assists), 0)::integer as total_assists,
                  round(coalesce(sum(mp.kills), 0)::numeric
                    / greatest(count(distinct coalesce(mp.match_game_id::text, mp.match_id::text)), 1), 2) as avg_kills,
                  round(coalesce(sum(mp.deaths), 0)::numeric
                    / greatest(count(distinct coalesce(mp.match_game_id::text, mp.match_id::text)), 1), 2) as avg_deaths,
                  round(coalesce(sum(mp.assists), 0)::numeric
                    / greatest(count(distinct coalesce(mp.match_game_id::text, mp.match_id::text)), 1), 2) as avg_assists,
                  round((coalesce(sum(mp.kills), 0) + coalesce(sum(mp.assists), 0))::numeric
                    / greatest(coalesce(sum(mp.deaths), 0), 1), 2) as avg_kda,
                  round(avg(mp.gold_per_min), 2) as avg_gpm,
                  round(avg(mp.xp_per_min), 2) as avg_xpm,
                  round(avg(mp.hero_damage), 2) as avg_hero_damage
                from public.match_players mp
                left join public.match_games mg on mg.id = mp.match_game_id
                join public.matches m on m.id = coalesce(mg.match_id, mp.match_id)
                join public.tournaments t on t.id = m.tournament_id
                join public.teams tm on tm.id = mp.team_id
                where """ + tournamentVisibilityCondition(publicOnly) + """
                  and mp.team_id is not null
                """ + queryParts.sql() + """
                group by tm.id, tm.name, m.tournament_id, t.title
                order by games_played desc, win_rate desc, tm.name asc
                limit ?
                """,
                this::mapTeamMetrics,
                parameters.toArray());
    }

    public List<HeroMetrics> findHeroMetrics(AnalyticsFilters filters) {
        return findHeroMetrics(filters, true);
    }

    public List<HeroMetrics> findProtectedHeroMetrics(AnalyticsFilters filters) {
        return findHeroMetrics(filters, false);
    }

    public List<HeroMetrics> findHeroMetricsForTeams(
            UUID firstTeamId,
            UUID secondTeamId,
            AnalyticsFilters filters,
            boolean publicOnly
    ) {
        AnalyticsFilters scopedFilters = new AnalyticsFilters(
                filters.tournamentId(),
                null,
                filters.profileId(),
                filters.heroId(),
                filters.from(),
                filters.to(),
                filters.limit());
        QueryParts queryParts = filteredWhere(scopedFilters, "mp", "m");
        List<Object> parameters = new ArrayList<>();
        parameters.add(firstTeamId);
        parameters.add(secondTeamId);
        parameters.addAll(queryParts.parameters());
        parameters.add(scopedFilters.limit());

        return jdbcTemplate.query(
                """
                select
                  h.id as hero_id,
                  h.dota_hero_id,
                  h.name,
                  h.localized_name,
                  h.image_url,
                  h.icon_url,
                  m.tournament_id,
                  t.title as tournament_name,
                  count(*)::integer as games_played,
                  count(*) filter (where mp.is_winner is true)::integer as wins,
                  count(*) filter (where mp.is_winner is false)::integer as losses,
                  round(((count(*) filter (where mp.is_winner is true))::numeric / greatest(count(*), 1)) * 100, 2)
                    as win_rate,
                  coalesce(sum(mp.kills), 0)::integer as total_kills,
                  coalesce(sum(mp.deaths), 0)::integer as total_deaths,
                  coalesce(sum(mp.assists), 0)::integer as total_assists,
                  round(avg(mp.kills), 2) as avg_kills,
                  round(avg(mp.deaths), 2) as avg_deaths,
                  round(avg(mp.assists), 2) as avg_assists,
                  round((coalesce(sum(mp.kills), 0) + coalesce(sum(mp.assists), 0))::numeric
                    / greatest(coalesce(sum(mp.deaths), 0), 1), 2) as kda,
                  round(avg(mp.gold_per_min), 2) as avg_gpm,
                  round(avg(mp.xp_per_min), 2) as avg_xpm,
                  round(avg(mp.hero_damage), 2) as avg_hero_damage
                from public.match_players mp
                left join public.match_games mg on mg.id = mp.match_game_id
                join public.matches m on m.id = coalesce(mg.match_id, mp.match_id)
                join public.tournaments t on t.id = m.tournament_id
                join public.heroes h on h.id = mp.hero_id
                where """ + tournamentVisibilityCondition(publicOnly) + """
                  and mp.team_id in (?, ?)
                  and mp.hero_id is not null
                """ + queryParts.sql() + """
                group by h.id, h.dota_hero_id, h.name, h.localized_name, h.image_url, h.icon_url, m.tournament_id, t.title
                order by games_played desc, win_rate desc, h.localized_name asc
                limit ?
                """,
                this::mapHeroMetrics,
                parameters.toArray());
    }

    private List<HeroMetrics> findHeroMetrics(AnalyticsFilters filters, boolean publicOnly) {
        QueryParts queryParts = filteredWhere(filters, "mp", "m");
        List<Object> parameters = new ArrayList<>(queryParts.parameters());
        parameters.add(filters.limit());

        return jdbcTemplate.query(
                """
                select
                  h.id as hero_id,
                  h.dota_hero_id,
                  h.name,
                  h.localized_name,
                  h.image_url,
                  h.icon_url,
                  m.tournament_id,
                  t.title as tournament_name,
                  count(*)::integer as games_played,
                  count(*) filter (where mp.is_winner is true)::integer as wins,
                  count(*) filter (where mp.is_winner is false)::integer as losses,
                  round(((count(*) filter (where mp.is_winner is true))::numeric / greatest(count(*), 1)) * 100, 2)
                    as win_rate,
                  coalesce(sum(mp.kills), 0)::integer as total_kills,
                  coalesce(sum(mp.deaths), 0)::integer as total_deaths,
                  coalesce(sum(mp.assists), 0)::integer as total_assists,
                  round(avg(mp.kills), 2) as avg_kills,
                  round(avg(mp.deaths), 2) as avg_deaths,
                  round(avg(mp.assists), 2) as avg_assists,
                  round((coalesce(sum(mp.kills), 0) + coalesce(sum(mp.assists), 0))::numeric
                    / greatest(coalesce(sum(mp.deaths), 0), 1), 2) as kda,
                  round(avg(mp.gold_per_min), 2) as avg_gpm,
                  round(avg(mp.xp_per_min), 2) as avg_xpm,
                  round(avg(mp.hero_damage), 2) as avg_hero_damage
                from public.match_players mp
                left join public.match_games mg on mg.id = mp.match_game_id
                join public.matches m on m.id = coalesce(mg.match_id, mp.match_id)
                join public.tournaments t on t.id = m.tournament_id
                join public.heroes h on h.id = mp.hero_id
                where """ + tournamentVisibilityCondition(publicOnly) + """
                  and mp.hero_id is not null
                """ + queryParts.sql() + """
                group by h.id, h.dota_hero_id, h.name, h.localized_name, h.image_url, h.icon_url, m.tournament_id, t.title
                order by games_played desc, win_rate desc, h.localized_name asc
                limit ?
                """,
                this::mapHeroMetrics,
                parameters.toArray());
    }

    public List<PlayerHeroPerformance> findPlayerHeroPerformance(
            UUID profileId,
            AnalyticsFilters filters,
            boolean publicOnly
    ) {
        AnalyticsFilters scopedFilters = filters.withProfileId(profileId);
        QueryParts queryParts = filteredWhere(scopedFilters, "mp", "m");
        List<Object> parameters = new ArrayList<>(queryParts.parameters());
        parameters.add(scopedFilters.limit());

        return jdbcTemplate.query(
                """
                with filtered_rows as (
                  select
                    h.id as hero_id,
                    coalesce(mp.dota_hero_id, h.dota_hero_id) as dota_hero_id,
                    coalesce(h.localized_name, h.name, 'Unknown hero') as hero_name,
                    m.id as match_id,
                    mg.id as match_game_id,
                    coalesce(mg.dota_match_id, m.dota_match_id) as dota_match_id,
                    """ + analyticsTimestampExpression("mg", "m") + """
                      as played_at,
                    mp.is_winner as won,
                    coalesce(mp.kills, 0)::integer as kills,
                    coalesce(mp.deaths, 0)::integer as deaths,
                    coalesce(mp.assists, 0)::integer as assists,
                    round((coalesce(mp.kills, 0) + coalesce(mp.assists, 0))::numeric
                      / greatest(coalesce(mp.deaths, 0), 1), 2) as match_kda,
                    mp.gold_per_min,
                    mp.xp_per_min,
                    mp.hero_damage,
                    mp.tower_damage,
                    mp.hero_healing,
                    mp.last_hits,
                    mp.denies
                  from public.match_players mp
                  left join public.match_games mg on mg.id = mp.match_game_id
                  join public.matches m on m.id = coalesce(mg.match_id, mp.match_id)
                  join public.tournaments t on t.id = m.tournament_id
                  join public.heroes h on h.id = mp.hero_id
                  where """ + tournamentVisibilityCondition(publicOnly) + """
                    and mp.profile_id is not null
                    and mp.hero_id is not null
                """ + queryParts.sql() + """
                ),
                ranked_rows as (
                  select
                    filtered_rows.*,
                    row_number() over (
                      partition by hero_id
                      order by played_at desc nulls last, match_id desc, match_game_id desc nulls last
                    ) as recent_rank,
                    row_number() over (
                      partition by hero_id
                      order by match_kda desc, kills desc, assists desc, played_at desc nulls last, match_id desc, match_game_id desc nulls last
                    ) as best_rank
                  from filtered_rows
                ),
                hero_agg as (
                  select
                    hero_id,
                    dota_hero_id,
                    hero_name,
                    count(*)::integer as matches,
                    count(*) filter (where won is true)::integer as wins,
                    count(*) filter (where won is false)::integer as losses,
                    round(((count(*) filter (where won is true))::numeric / greatest(count(*), 1)) * 100, 2)
                      as win_rate,
                    round(avg(kills), 2) as avg_kills,
                    round(avg(deaths), 2) as avg_deaths,
                    round(avg(assists), 2) as avg_assists,
                    round(avg(match_kda), 2) as avg_kda,
                    round(avg(gold_per_min), 2) as avg_gpm,
                    round(avg(xp_per_min), 2) as avg_xpm,
                    round(avg(hero_damage), 2) as avg_hero_damage,
                    round(avg(tower_damage), 2) as avg_tower_damage,
                    round(avg(hero_healing), 2) as avg_hero_healing,
                    round(avg(last_hits), 2) as avg_last_hits,
                    round(avg(denies), 2) as avg_denies
                  from ranked_rows
                  group by hero_id, dota_hero_id, hero_name
                )
                select
                  hero_agg.*,
                  recent_row.match_id as recent_match_id,
                  recent_row.match_game_id as recent_match_game_id,
                  recent_row.dota_match_id as recent_dota_match_id,
                  recent_row.played_at as recent_played_at,
                  best_row.match_id as best_match_id,
                  best_row.match_game_id as best_match_game_id,
                  best_row.dota_match_id as best_dota_match_id,
                  best_row.played_at as best_played_at,
                  best_row.match_kda as best_kda
                from hero_agg
                left join ranked_rows recent_row
                  on recent_row.hero_id = hero_agg.hero_id
                  and recent_row.recent_rank = 1
                left join ranked_rows best_row
                  on best_row.hero_id = hero_agg.hero_id
                  and best_row.best_rank = 1
                order by hero_agg.matches desc, hero_agg.win_rate desc, hero_agg.avg_kda desc, hero_agg.hero_name asc
                limit ?
                """,
                this::mapPlayerHeroPerformance,
                parameters.toArray());
    }

    public List<TournamentMetrics> findTournamentMetrics(AnalyticsFilters filters) {
        return findTournamentMetrics(filters, true);
    }

    public List<TournamentMetrics> findProtectedTournamentMetrics(AnalyticsFilters filters) {
        return findTournamentMetrics(filters, false);
    }

    public List<HeroMetrics> findSharedHeroesForPlayers(
            UUID firstProfileId,
            UUID secondProfileId,
            AnalyticsFilters filters,
            boolean publicOnly
    ) {
        AnalyticsFilters scopedFilters = new AnalyticsFilters(
                filters.tournamentId(),
                filters.teamId(),
                null,
                filters.heroId(),
                filters.from(),
                filters.to(),
                filters.limit());
        QueryParts queryParts = filteredWhere(scopedFilters, "mp", "m");
        List<Object> parameters = new ArrayList<>();
        parameters.add(firstProfileId);
        parameters.add(secondProfileId);
        parameters.addAll(queryParts.parameters());
        parameters.add(scopedFilters.limit());

        return jdbcTemplate.query(
                """
                with filtered_players as (
                  select
                    h.id as hero_id,
                    h.dota_hero_id,
                    h.name,
                    h.localized_name,
                    h.image_url,
                    h.icon_url,
                    mp.profile_id,
                    mp.is_winner,
                    mp.kills,
                    mp.deaths,
                    mp.assists,
                    mp.gold_per_min,
                    mp.xp_per_min,
                    mp.hero_damage
                  from public.match_players mp
                  left join public.match_games mg on mg.id = mp.match_game_id
                  join public.matches m on m.id = coalesce(mg.match_id, mp.match_id)
                  join public.tournaments t on t.id = m.tournament_id
                  join public.heroes h on h.id = mp.hero_id
                  where """ + tournamentVisibilityCondition(publicOnly) + """
                    and mp.profile_id in (?, ?)
                    and mp.hero_id is not null
                """ + queryParts.sql() + """
                )
                select
                  hero_id,
                  dota_hero_id,
                  name,
                  localized_name,
                  image_url,
                  icon_url,
                  null::uuid as tournament_id,
                  null::text as tournament_name,
                  count(*)::integer as games_played,
                  count(*) filter (where is_winner is true)::integer as wins,
                  count(*) filter (where is_winner is false)::integer as losses,
                  round(((count(*) filter (where is_winner is true))::numeric / greatest(count(*), 1)) * 100, 2)
                    as win_rate,
                  coalesce(sum(kills), 0)::integer as total_kills,
                  coalesce(sum(deaths), 0)::integer as total_deaths,
                  coalesce(sum(assists), 0)::integer as total_assists,
                  round(avg(kills), 2) as avg_kills,
                  round(avg(deaths), 2) as avg_deaths,
                  round(avg(assists), 2) as avg_assists,
                  round((coalesce(sum(kills), 0) + coalesce(sum(assists), 0))::numeric
                    / greatest(coalesce(sum(deaths), 0), 1), 2) as kda,
                  round(avg(gold_per_min), 2) as avg_gpm,
                  round(avg(xp_per_min), 2) as avg_xpm,
                  round(avg(hero_damage), 2) as avg_hero_damage
                from filtered_players
                group by hero_id, dota_hero_id, name, localized_name, image_url, icon_url
                having count(distinct profile_id) = 2
                order by games_played desc, win_rate desc, localized_name asc
                limit ?
                """,
                this::mapHeroMetrics,
                parameters.toArray());
    }

    public List<AnalyticsMatchHistory> findRecentMatchesForPlayers(
            UUID firstProfileId,
            UUID secondProfileId,
            AnalyticsFilters filters,
            boolean publicOnly
    ) {
        AnalyticsFilters scopedFilters = new AnalyticsFilters(
                filters.tournamentId(),
                filters.teamId(),
                null,
                filters.heroId(),
                filters.from(),
                filters.to(),
                filters.limit());
        QueryParts queryParts = filteredWhere(scopedFilters, "mp", "m");
        List<Object> parameters = new ArrayList<>();
        parameters.add(firstProfileId);
        parameters.add(secondProfileId);
        parameters.addAll(queryParts.parameters());
        parameters.add(scopedFilters.limit());

        return jdbcTemplate.query(
                recentMatchesSql("mp.profile_id in (?, ?)", "mp.profile_id", queryParts.sql(), publicOnly),
                this::mapMatchHistory,
                parameters.toArray());
    }

    public List<PlayerComparisonMatch> findPlayerComparisonMatches(
            UUID firstProfileId,
            UUID secondProfileId,
            AnalyticsFilters filters,
            boolean publicOnly
    ) {
        AnalyticsFilters scopedFilters = new AnalyticsFilters(
                filters.tournamentId(),
                filters.teamId(),
                null,
                filters.heroId(),
                filters.from(),
                filters.to(),
                filters.limit());
        QueryParts queryParts = filteredWhere(scopedFilters, "mp", "m");
        List<Object> parameters = new ArrayList<>();
        parameters.add(firstProfileId);
        parameters.add(secondProfileId);
        parameters.addAll(queryParts.parameters());
        parameters.add(firstProfileId);
        parameters.add(secondProfileId);
        parameters.add(scopedFilters.limit());

        return jdbcTemplate.query(
                """
                with filtered_players as (
                  select
                    coalesce(mg.id::text, m.id::text) as game_key,
                    m.id as match_id,
                    mg.id as match_game_id,
                    coalesce(mg.dota_match_id, m.dota_match_id) as dota_match_id,
                    m.tournament_id,
                    t.title as tournament_name,
                    """ + analyticsTimestampExpression("mg", "m") + """
                      as played_at,
                    m.team_a_id,
                    ta.name as team_a_name,
                    m.team_b_id,
                    tb.name as team_b_name,
                    coalesce(mg.winner_team_id, m.winner_team_id) as winner_team_id,
                    coalesce(
                      mg.winner_side::text,
                      case
                        when coalesce(mg.winner_team_id, m.winner_team_id) = mp.team_id then
                          coalesce(mp.team_side::text, case when mp.is_radiant is true then 'RADIANT' when mp.is_radiant is false then 'DIRE' else null end)
                        else null
                      end
                    ) as winner_side,
                    mp.profile_id,
                    mp.team_id,
                    tm.name as team_name,
                    h.id as hero_id,
                    coalesce(mp.dota_hero_id, h.dota_hero_id) as dota_hero_id,
                    coalesce(h.localized_name, h.name) as hero_name,
                    mp.is_winner as won,
                    coalesce(mp.kills, 0)::integer as kills,
                    coalesce(mp.deaths, 0)::integer as deaths,
                    coalesce(mp.assists, 0)::integer as assists,
                    round((coalesce(mp.kills, 0) + coalesce(mp.assists, 0))::numeric
                      / greatest(coalesce(mp.deaths, 0), 1), 2) as kda,
                    mp.gold_per_min,
                    mp.xp_per_min,
                    mp.last_hits,
                    mp.denies,
                    mp.net_worth,
                    mp.hero_damage,
                    mp.tower_damage,
                    mp.hero_healing,
                    coalesce(mp.team_side::text, case when mp.is_radiant is true then 'RADIANT' when mp.is_radiant is false then 'DIRE' else null end)
                      as team_side
                  from public.match_players mp
                  left join public.match_games mg on mg.id = mp.match_game_id
                  join public.matches m on m.id = coalesce(mg.match_id, mp.match_id)
                  join public.tournaments t on t.id = m.tournament_id
                  left join public.teams ta on ta.id = m.team_a_id
                  left join public.teams tb on tb.id = m.team_b_id
                  left join public.teams tm on tm.id = mp.team_id
                  left join public.heroes h on h.id = mp.hero_id
                  where """ + tournamentVisibilityCondition(publicOnly) + """
                    and mp.profile_id in (?, ?)
                    and mp.profile_id is not null
                """ + queryParts.sql() + """
                ),
                ranked_players as (
                  select
                    filtered_players.*,
                    row_number() over (
                      partition by game_key, profile_id
                      order by played_at desc nulls last, match_id desc, match_game_id desc nulls last
                    ) as player_rank
                  from filtered_players
                )
                select
                  a.match_id,
                  a.match_game_id,
                  a.dota_match_id,
                  a.tournament_id,
                  a.tournament_name,
                  a.played_at,
                  a.team_a_id,
                  a.team_a_name,
                  a.team_b_id,
                  a.team_b_name,
                  a.winner_team_id,
                  coalesce(a.winner_side, b.winner_side) as winner_side,
                  a.profile_id as a_profile_id,
                  a.team_id as a_team_id,
                  a.team_name as a_team_name,
                  a.hero_id as a_hero_id,
                  a.dota_hero_id as a_dota_hero_id,
                  a.hero_name as a_hero_name,
                  a.won as a_won,
                  a.kills as a_kills,
                  a.deaths as a_deaths,
                  a.assists as a_assists,
                  a.kda as a_kda,
                  a.gold_per_min as a_gold_per_min,
                  a.xp_per_min as a_xp_per_min,
                  a.last_hits as a_last_hits,
                  a.denies as a_denies,
                  a.net_worth as a_net_worth,
                  a.hero_damage as a_hero_damage,
                  a.tower_damage as a_tower_damage,
                  a.hero_healing as a_hero_healing,
                  a.team_side as a_team_side,
                  b.profile_id as b_profile_id,
                  b.team_id as b_team_id,
                  b.team_name as b_team_name,
                  b.hero_id as b_hero_id,
                  b.dota_hero_id as b_dota_hero_id,
                  b.hero_name as b_hero_name,
                  b.won as b_won,
                  b.kills as b_kills,
                  b.deaths as b_deaths,
                  b.assists as b_assists,
                  b.kda as b_kda,
                  b.gold_per_min as b_gold_per_min,
                  b.xp_per_min as b_xp_per_min,
                  b.last_hits as b_last_hits,
                  b.denies as b_denies,
                  b.net_worth as b_net_worth,
                  b.hero_damage as b_hero_damage,
                  b.tower_damage as b_tower_damage,
                  b.hero_healing as b_hero_healing,
                  b.team_side as b_team_side
                from ranked_players a
                join ranked_players b on b.game_key = a.game_key
                where a.profile_id = ?
                  and b.profile_id = ?
                  and a.player_rank = 1
                  and b.player_rank = 1
                order by a.played_at desc nulls last, a.match_id desc, a.match_game_id desc nulls last
                limit ?
                """,
                this::mapPlayerComparisonMatch,
                parameters.toArray());
    }

    public List<AnalyticsMatchHistory> findRecentMatchesForPlayer(
            UUID profileId,
            AnalyticsFilters filters,
            boolean publicOnly
    ) {
        AnalyticsFilters scopedFilters = new AnalyticsFilters(
                filters.tournamentId(),
                filters.teamId(),
                null,
                filters.heroId(),
                filters.from(),
                filters.to(),
                filters.limit());
        QueryParts queryParts = filteredWhere(scopedFilters, "mp", "m");
        List<Object> parameters = new ArrayList<>();
        parameters.add(profileId);
        parameters.addAll(queryParts.parameters());
        parameters.add(scopedFilters.limit());

        return jdbcTemplate.query(
                recentMatchesSql("mp.profile_id = ?", "mp.profile_id", queryParts.sql(), publicOnly, 1),
                this::mapMatchHistory,
                parameters.toArray());
    }

    public List<PlayerProgressPoint> findPlayerProgress(
            UUID profileId,
            AnalyticsFilters filters,
            boolean publicOnly
    ) {
        AnalyticsFilters scopedFilters = filters.withProfileId(profileId);
        QueryParts queryParts = filteredWhere(scopedFilters, "mp", "m");
        List<Object> parameters = new ArrayList<>(queryParts.parameters());
        parameters.add(scopedFilters.limit());

        return jdbcTemplate.query(
                """
                select *
                from (
                  select
                    """ + analyticsTimestampExpression("mg", "m") + """
                      as played_at,
                    m.id as match_id,
                    mg.id as match_game_id,
                    coalesce(mg.dota_match_id, m.dota_match_id) as dota_match_id,
                    h.id as hero_id,
                    coalesce(mp.dota_hero_id, h.dota_hero_id) as dota_hero_id,
                    coalesce(h.localized_name, h.name) as hero_name,
                    coalesce(mp.kills, 0)::integer as kills,
                    coalesce(mp.deaths, 0)::integer as deaths,
                    coalesce(mp.assists, 0)::integer as assists,
                    round((coalesce(mp.kills, 0) + coalesce(mp.assists, 0))::numeric
                      / greatest(coalesce(mp.deaths, 0), 1), 2) as kda,
                    mp.gold_per_min,
                    mp.xp_per_min,
                    mp.hero_damage,
                    mp.tower_damage,
                    mp.hero_healing,
                    mp.last_hits,
                    mp.denies,
                    mp.is_winner as won,
                    mp.net_worth,
                    mp.level,
                    coalesce(mg.duration_seconds, mp.duration_seconds) as duration_seconds,
                    mp.team_side,
                    mg.radiant_score,
                    mg.dire_score,
                    mg.winner_side
                  from public.match_players mp
                  left join public.match_games mg on mg.id = mp.match_game_id
                  join public.matches m on m.id = coalesce(mg.match_id, mp.match_id)
                  join public.tournaments t on t.id = m.tournament_id
                  left join public.heroes h on h.id = mp.hero_id
                  where """ + tournamentVisibilityCondition(publicOnly) + """
                    and mp.profile_id is not null
                """ + queryParts.sql() + """
                  order by played_at desc nulls last, match_id desc, match_game_id desc nulls last
                  limit ?
                ) progress
                order by played_at asc nulls last, match_id asc, match_game_id asc nulls last
                """,
                this::mapPlayerProgressPoint,
                parameters.toArray());
    }

    public List<AnalyticsMatchHistory> findRecentMatchesForTeams(
            UUID firstTeamId,
            UUID secondTeamId,
            AnalyticsFilters filters,
            boolean publicOnly
    ) {
        AnalyticsFilters scopedFilters = new AnalyticsFilters(
                filters.tournamentId(),
                null,
                filters.profileId(),
                filters.heroId(),
                filters.from(),
                filters.to(),
                filters.limit());
        QueryParts queryParts = filteredWhere(scopedFilters, "mp", "m");
        List<Object> parameters = new ArrayList<>();
        parameters.add(firstTeamId);
        parameters.add(secondTeamId);
        parameters.addAll(queryParts.parameters());
        parameters.add(scopedFilters.limit());

        return jdbcTemplate.query(
                recentMatchesSql("mp.team_id in (?, ?)", "mp.team_id", queryParts.sql(), publicOnly),
                this::mapMatchHistory,
                parameters.toArray());
    }

    public List<AnalyticsMatchHistory> findRecentMatchesForTeam(
            UUID teamId,
            AnalyticsFilters filters,
            boolean publicOnly
    ) {
        AnalyticsFilters scopedFilters = new AnalyticsFilters(
                filters.tournamentId(),
                null,
                filters.profileId(),
                filters.heroId(),
                filters.from(),
                filters.to(),
                filters.limit());
        QueryParts queryParts = filteredWhere(scopedFilters, "mp", "m");
        List<Object> parameters = new ArrayList<>();
        parameters.add(teamId);
        parameters.addAll(queryParts.parameters());
        parameters.add(scopedFilters.limit());

        return jdbcTemplate.query(
                recentMatchesSql("mp.team_id = ?", "mp.team_id", queryParts.sql(), publicOnly, 1),
                this::mapMatchHistory,
                parameters.toArray());
    }

    private List<TournamentMetrics> findTournamentMetrics(AnalyticsFilters filters, boolean publicOnly) {
        QueryParts queryParts = filteredWhere(filters, "mp", "m");
        List<Object> parameters = new ArrayList<>(queryParts.parameters());
        parameters.add(filters.limit());

        List<TournamentMetrics> metrics = jdbcTemplate.query(
                tournamentMetricsSql(queryParts.sql(), publicOnly) + """
                order by player_agg.games_played desc, player_agg.tournament_name asc
                limit ?
                """,
                this::mapTournamentMetricsWithoutPickedHeroes,
                parameters.toArray());

        return metrics.stream()
                .map(metric -> withPickedHeroes(metric, filters.withTournamentId(metric.tournamentId()), publicOnly))
                .toList();
    }

    public Optional<TournamentMetrics> findTournamentMetricsById(UUID tournamentId) {
        return findTournamentMetricsById(tournamentId, true);
    }

    public Optional<TournamentMetrics> findProtectedTournamentMetricsById(UUID tournamentId) {
        return findTournamentMetricsById(tournamentId, false);
    }

    private Optional<TournamentMetrics> findTournamentMetricsById(UUID tournamentId, boolean publicOnly) {
        AnalyticsFilters filters = new AnalyticsFilters(tournamentId, null, null, null, 1);
        QueryParts queryParts = filteredWhere(filters, "mp", "m");

        return jdbcTemplate.query(
                        tournamentMetricsSql(queryParts.sql(), publicOnly),
                        this::mapTournamentMetricsWithoutPickedHeroes,
                        queryParts.parameters().toArray())
                .stream()
                .findFirst()
                .map(metric -> withPickedHeroes(metric, filters, publicOnly));
    }

    private TournamentMetrics withPickedHeroes(
            TournamentMetrics metrics,
            AnalyticsFilters filters,
            boolean publicOnly
    ) {
        return new TournamentMetrics(
                metrics.tournamentId(),
                metrics.tournamentName(),
                metrics.gamesPlayed(),
                metrics.teamsCount(),
                metrics.playersCount(),
                metrics.heroesPickedCount(),
                metrics.avgDurationSeconds(),
                metrics.totalKills(),
                metrics.totalDeaths(),
                metrics.totalAssists(),
                metrics.avgKillsPerGame(),
                metrics.avgKda(),
                findMostPickedHeroes(filters, publicOnly));
    }

    private List<PickedHeroMetrics> findMostPickedHeroes(AnalyticsFilters filters, boolean publicOnly) {
        QueryParts queryParts = filteredWhere(filters, "mp", "m");
        List<Object> parameters = new ArrayList<>(queryParts.parameters());
        parameters.add(5);

        return jdbcTemplate.query(
                """
                select
                  h.id as hero_id,
                  h.dota_hero_id,
                  h.localized_name,
                  h.image_url,
                  h.icon_url,
                  count(*)::integer as picks
                from public.match_players mp
                left join public.match_games mg on mg.id = mp.match_game_id
                join public.matches m on m.id = coalesce(mg.match_id, mp.match_id)
                join public.tournaments t on t.id = m.tournament_id
                join public.heroes h on h.id = mp.hero_id
                where """ + tournamentVisibilityCondition(publicOnly) + """
                  and mp.hero_id is not null
                """ + queryParts.sql() + """
                group by h.id, h.dota_hero_id, h.localized_name, h.image_url, h.icon_url
                order by picks desc, h.localized_name asc
                limit ?
                """,
                this::mapPickedHeroMetrics,
                parameters.toArray());
    }

    private String tournamentMetricsSql(String filteredWhere, boolean publicOnly) {
        return """
                with filtered_players as (
                  select
                    m.tournament_id,
                    t.title as tournament_name,
                    coalesce(mp.match_game_id::text, mp.match_id::text) as game_key,
                    coalesce(mg.duration_seconds, mp.duration_seconds) as duration_seconds,
                    mp.team_id,
                    mp.profile_id,
                    mp.hero_id,
                    mp.kills,
                    mp.deaths,
                    mp.assists
                  from public.match_players mp
                  left join public.match_games mg on mg.id = mp.match_game_id
                  join public.matches m on m.id = coalesce(mg.match_id, mp.match_id)
                  join public.tournaments t on t.id = m.tournament_id
                  where """ + tournamentVisibilityCondition(publicOnly) + """
                """ + filteredWhere + """
                ),
                player_agg as (
                  select
                    tournament_id,
                    tournament_name,
                    count(distinct game_key)::integer as games_played,
                    count(distinct team_id)::integer as teams_count,
                    count(distinct profile_id)::integer as players_count,
                    count(distinct hero_id)::integer as heroes_picked_count,
                    coalesce(sum(kills), 0)::integer as total_kills,
                    coalesce(sum(deaths), 0)::integer as total_deaths,
                    coalesce(sum(assists), 0)::integer as total_assists,
                    round(coalesce(sum(kills), 0)::numeric / greatest(count(distinct game_key), 1), 2) as avg_kills_per_game,
                    round((coalesce(sum(kills), 0) + coalesce(sum(assists), 0))::numeric
                      / greatest(coalesce(sum(deaths), 0), 1), 2) as avg_kda
                  from filtered_players
                  group by tournament_id, tournament_name
                ),
                game_durations as (
                  select
                    tournament_id,
                    game_key,
                    max(duration_seconds) as duration_seconds
                  from filtered_players
                  group by tournament_id, game_key
                ),
                duration_agg as (
                  select
                    tournament_id,
                    round(avg(duration_seconds))::integer as avg_duration_seconds
                  from game_durations
                  where duration_seconds is not null
                  group by tournament_id
                )
                select
                  player_agg.tournament_id,
                  player_agg.tournament_name,
                  player_agg.games_played,
                  player_agg.teams_count,
                  player_agg.players_count,
                  player_agg.heroes_picked_count,
                  duration_agg.avg_duration_seconds,
                  player_agg.total_kills,
                  player_agg.total_deaths,
                  player_agg.total_assists,
                  player_agg.avg_kills_per_game,
                  player_agg.avg_kda
                from player_agg
                left join duration_agg on duration_agg.tournament_id = player_agg.tournament_id
                """;
    }

    private String recentMatchesSql(
            String subjectCondition,
            String subjectDistinctColumn,
            String filteredWhere,
            boolean publicOnly
    ) {
        return recentMatchesSql(subjectCondition, subjectDistinctColumn, filteredWhere, publicOnly, 2);
    }

    private String recentMatchesSql(
            String subjectCondition,
            String subjectDistinctColumn,
            String filteredWhere,
            boolean publicOnly,
            int requiredSubjectCount
    ) {
        return """
                select
                  m.id as match_id,
                  mg.id as match_game_id,
                  coalesce(mg.dota_match_id, m.dota_match_id) as dota_match_id,
                  m.tournament_id,
                  t.title as tournament_name,
                  """ + analyticsTimestampExpression("mg", "m") + """ 
                    as played_at,
                  m.team_a_id,
                  ta.name as team_a_name,
                  m.team_b_id,
                  tb.name as team_b_name,
                  coalesce(mg.winner_team_id, m.winner_team_id) as winner_team_id
                from public.match_players mp
                left join public.match_games mg on mg.id = mp.match_game_id
                join public.matches m on m.id = coalesce(mg.match_id, mp.match_id)
                join public.tournaments t on t.id = m.tournament_id
                left join public.teams ta on ta.id = m.team_a_id
                left join public.teams tb on tb.id = m.team_b_id
                """ + "where " + tournamentVisibilityCondition(publicOnly).trim() + "\n"
                + "  and " + subjectCondition + "\n"
                + filteredWhere + """
                group by
                  m.id,
                  mg.id,
                  mg.dota_match_id,
                  m.dota_match_id,
                  m.tournament_id,
                  t.title,
                  mg.started_at,
                  mg.finished_at,
                  m.started_at,
                  m.finished_at,
                  m.scheduled_at,
                  mg.created_at,
                  m.created_at,
                  m.team_a_id,
                  ta.name,
                  m.team_b_id,
                  tb.name,
                  mg.winner_team_id,
                  m.winner_team_id
                """ + "having count(distinct " + subjectDistinctColumn + ") = " + requiredSubjectCount + "\n" + """
                order by played_at desc nulls last, m.id desc, mg.id desc nulls last
                limit ?
                """;
    }

    private QueryParts filteredWhere(AnalyticsFilters filters, String playerAlias, String matchAlias) {
        List<String> clauses = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        String analyticsTimestamp = analyticsTimestampExpression("mg", matchAlias);

        if (filters.tournamentId() != null) {
            clauses.add(matchAlias + ".tournament_id = ?");
            parameters.add(filters.tournamentId());
        }
        if (filters.teamId() != null) {
            clauses.add(playerAlias + ".team_id = ?");
            parameters.add(filters.teamId());
        }
        if (filters.profileId() != null) {
            clauses.add(playerAlias + ".profile_id = ?");
            parameters.add(filters.profileId());
        }
        if (filters.heroId() != null) {
            clauses.add(playerAlias + ".hero_id = ?");
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
            return new QueryParts("", parameters);
        }

        return new QueryParts(" and " + String.join(" and ", clauses) + "\n", parameters);
    }

    private String analyticsTimestampExpression(String matchGameAlias, String matchAlias) {
        return "coalesce("
                + matchGameAlias + ".started_at, "
                + matchGameAlias + ".finished_at, "
                + matchAlias + ".started_at, "
                + matchAlias + ".finished_at, "
                + matchAlias + ".scheduled_at, "
                + matchGameAlias + ".created_at, "
                + matchAlias + ".created_at)";
    }

    private String tournamentVisibilityCondition(boolean publicOnly) {
        return publicOnly ? " t.is_public = true" : " true";
    }

    private PlayerMetrics mapPlayerMetrics(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PlayerMetrics(
                resultSet.getObject("profile_id", UUID.class),
                resultSet.getString("display_name"),
                resultSet.getObject("team_id", UUID.class),
                resultSet.getString("team_name"),
                resultSet.getObject("tournament_id", UUID.class),
                resultSet.getString("tournament_name"),
                resultSet.getInt("games_played"),
                resultSet.getInt("wins"),
                resultSet.getInt("losses"),
                decimal(resultSet, "win_rate"),
                resultSet.getInt("kills"),
                resultSet.getInt("deaths"),
                resultSet.getInt("assists"),
                decimal(resultSet, "avg_kills"),
                decimal(resultSet, "avg_deaths"),
                decimal(resultSet, "avg_assists"),
                decimal(resultSet, "kda"),
                decimal(resultSet, "avg_gpm"),
                decimal(resultSet, "avg_xpm"),
                decimal(resultSet, "avg_hero_damage"));
    }

    private PlayerComparisonHeadlineMetrics mapPlayerComparisonHeadlineMetrics(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new PlayerComparisonHeadlineMetrics(
                resultSet.getObject("profile_id", UUID.class),
                resultSet.getString("display_name"),
                resultSet.getInt("games_played"),
                resultSet.getInt("wins"),
                resultSet.getInt("losses"),
                decimal(resultSet, "win_rate"),
                decimal(resultSet, "kda"),
                decimal(resultSet, "avg_kills"),
                decimal(resultSet, "avg_deaths"),
                decimal(resultSet, "avg_assists"),
                decimal(resultSet, "avg_gpm"),
                decimal(resultSet, "avg_xpm"),
                decimal(resultSet, "avg_last_hits"),
                decimal(resultSet, "avg_denies"),
                decimal(resultSet, "avg_net_worth"),
                decimal(resultSet, "avg_hero_damage"),
                decimal(resultSet, "avg_tower_damage"),
                decimal(resultSet, "avg_hero_healing"));
    }

    private TeamMetrics mapTeamMetrics(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TeamMetrics(
                resultSet.getObject("team_id", UUID.class),
                resultSet.getString("team_name"),
                resultSet.getObject("tournament_id", UUID.class),
                resultSet.getString("tournament_name"),
                resultSet.getInt("games_played"),
                resultSet.getInt("wins"),
                resultSet.getInt("losses"),
                decimal(resultSet, "win_rate"),
                resultSet.getInt("total_kills"),
                resultSet.getInt("total_deaths"),
                resultSet.getInt("total_assists"),
                decimal(resultSet, "avg_kills"),
                decimal(resultSet, "avg_deaths"),
                decimal(resultSet, "avg_assists"),
                decimal(resultSet, "avg_kda"),
                decimal(resultSet, "avg_gpm"),
                decimal(resultSet, "avg_xpm"),
                decimal(resultSet, "avg_hero_damage"));
    }

    private HeroMetrics mapHeroMetrics(ResultSet resultSet, int rowNumber) throws SQLException {
        return new HeroMetrics(
                resultSet.getObject("hero_id", UUID.class),
                resultSet.getObject("dota_hero_id", Integer.class),
                resultSet.getString("name"),
                resultSet.getString("localized_name"),
                resultSet.getString("image_url"),
                resultSet.getString("icon_url"),
                resultSet.getObject("tournament_id", UUID.class),
                resultSet.getString("tournament_name"),
                resultSet.getInt("games_played"),
                resultSet.getInt("wins"),
                resultSet.getInt("losses"),
                decimal(resultSet, "win_rate"),
                resultSet.getInt("total_kills"),
                resultSet.getInt("total_deaths"),
                resultSet.getInt("total_assists"),
                decimal(resultSet, "avg_kills"),
                decimal(resultSet, "avg_deaths"),
                decimal(resultSet, "avg_assists"),
                decimal(resultSet, "kda"),
                decimal(resultSet, "avg_gpm"),
                decimal(resultSet, "avg_xpm"),
                decimal(resultSet, "avg_hero_damage"));
    }

    private TournamentMetrics mapTournamentMetricsWithoutPickedHeroes(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new TournamentMetrics(
                resultSet.getObject("tournament_id", UUID.class),
                resultSet.getString("tournament_name"),
                resultSet.getInt("games_played"),
                resultSet.getInt("teams_count"),
                resultSet.getInt("players_count"),
                resultSet.getInt("heroes_picked_count"),
                resultSet.getObject("avg_duration_seconds", Integer.class),
                resultSet.getInt("total_kills"),
                resultSet.getInt("total_deaths"),
                resultSet.getInt("total_assists"),
                decimal(resultSet, "avg_kills_per_game"),
                decimal(resultSet, "avg_kda"),
                List.of());
    }

    private PickedHeroMetrics mapPickedHeroMetrics(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PickedHeroMetrics(
                resultSet.getObject("hero_id", UUID.class),
                resultSet.getObject("dota_hero_id", Integer.class),
                resultSet.getString("localized_name"),
                resultSet.getString("image_url"),
                resultSet.getString("icon_url"),
                resultSet.getInt("picks"));
    }

    private AnalyticsMatchHistory mapMatchHistory(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AnalyticsMatchHistory(
                resultSet.getObject("match_id", UUID.class),
                resultSet.getObject("match_game_id", UUID.class),
                resultSet.getString("dota_match_id"),
                resultSet.getObject("tournament_id", UUID.class),
                resultSet.getString("tournament_name"),
                resultSet.getObject("played_at", OffsetDateTime.class),
                resultSet.getObject("team_a_id", UUID.class),
                resultSet.getString("team_a_name"),
                resultSet.getObject("team_b_id", UUID.class),
                resultSet.getString("team_b_name"),
                resultSet.getObject("winner_team_id", UUID.class));
    }

    private PlayerComparisonMatch mapPlayerComparisonMatch(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PlayerComparisonMatch(
                resultSet.getObject("match_id", UUID.class),
                resultSet.getObject("match_game_id", UUID.class),
                resultSet.getString("dota_match_id"),
                resultSet.getObject("tournament_id", UUID.class),
                resultSet.getString("tournament_name"),
                resultSet.getObject("played_at", OffsetDateTime.class),
                resultSet.getObject("team_a_id", UUID.class),
                resultSet.getString("team_a_name"),
                resultSet.getObject("team_b_id", UUID.class),
                resultSet.getString("team_b_name"),
                resultSet.getObject("winner_team_id", UUID.class),
                resultSet.getString("winner_side"),
                mapPlayerComparisonMatchStats(resultSet, "a"),
                mapPlayerComparisonMatchStats(resultSet, "b"));
    }

    private PlayerComparisonMatch.PlayerMatchStats mapPlayerComparisonMatchStats(
            ResultSet resultSet,
            String prefix
    ) throws SQLException {
        String columnPrefix = prefix + "_";
        return new PlayerComparisonMatch.PlayerMatchStats(
                resultSet.getObject(columnPrefix + "profile_id", UUID.class),
                resultSet.getObject(columnPrefix + "team_id", UUID.class),
                resultSet.getString(columnPrefix + "team_name"),
                resultSet.getObject(columnPrefix + "hero_id", UUID.class),
                resultSet.getObject(columnPrefix + "dota_hero_id", Integer.class),
                resultSet.getString(columnPrefix + "hero_name"),
                resultSet.getObject(columnPrefix + "won", Boolean.class),
                resultSet.getInt(columnPrefix + "kills"),
                resultSet.getInt(columnPrefix + "deaths"),
                resultSet.getInt(columnPrefix + "assists"),
                decimal(resultSet, columnPrefix + "kda"),
                resultSet.getObject(columnPrefix + "gold_per_min", Integer.class),
                resultSet.getObject(columnPrefix + "xp_per_min", Integer.class),
                resultSet.getObject(columnPrefix + "last_hits", Integer.class),
                resultSet.getObject(columnPrefix + "denies", Integer.class),
                resultSet.getObject(columnPrefix + "net_worth", Integer.class),
                resultSet.getObject(columnPrefix + "hero_damage", Integer.class),
                resultSet.getObject(columnPrefix + "tower_damage", Integer.class),
                resultSet.getObject(columnPrefix + "hero_healing", Integer.class),
                resultSet.getString(columnPrefix + "team_side"));
    }

    private PlayerProgressPoint mapPlayerProgressPoint(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PlayerProgressPoint(
                resultSet.getObject("played_at", OffsetDateTime.class),
                resultSet.getObject("match_id", UUID.class),
                resultSet.getObject("match_game_id", UUID.class),
                resultSet.getString("dota_match_id"),
                resultSet.getObject("hero_id", UUID.class),
                resultSet.getObject("dota_hero_id", Integer.class),
                resultSet.getString("hero_name"),
                resultSet.getInt("kills"),
                resultSet.getInt("deaths"),
                resultSet.getInt("assists"),
                decimal(resultSet, "kda"),
                resultSet.getObject("gold_per_min", Integer.class),
                resultSet.getObject("xp_per_min", Integer.class),
                resultSet.getObject("hero_damage", Integer.class),
                resultSet.getObject("tower_damage", Integer.class),
                resultSet.getObject("hero_healing", Integer.class),
                resultSet.getObject("last_hits", Integer.class),
                resultSet.getObject("denies", Integer.class),
                resultSet.getObject("won", Boolean.class),
                resultSet.getObject("net_worth", Integer.class),
                resultSet.getObject("level", Integer.class),
                resultSet.getObject("duration_seconds", Integer.class),
                resultSet.getString("team_side"),
                resultSet.getObject("radiant_score", Integer.class),
                resultSet.getObject("dire_score", Integer.class),
                resultSet.getString("winner_side"));
    }

    private PlayerHeroPerformance mapPlayerHeroPerformance(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PlayerHeroPerformance(
                resultSet.getObject("hero_id", UUID.class),
                resultSet.getObject("dota_hero_id", Integer.class),
                resultSet.getString("hero_name"),
                resultSet.getInt("matches"),
                resultSet.getInt("wins"),
                resultSet.getInt("losses"),
                decimal(resultSet, "win_rate"),
                decimal(resultSet, "avg_kills"),
                decimal(resultSet, "avg_deaths"),
                decimal(resultSet, "avg_assists"),
                decimal(resultSet, "avg_kda"),
                decimal(resultSet, "avg_gpm"),
                decimal(resultSet, "avg_xpm"),
                decimal(resultSet, "avg_hero_damage"),
                decimal(resultSet, "avg_tower_damage"),
                decimal(resultSet, "avg_hero_healing"),
                decimal(resultSet, "avg_last_hits"),
                decimal(resultSet, "avg_denies"),
                resultSet.getObject("recent_match_id", UUID.class),
                resultSet.getObject("recent_match_game_id", UUID.class),
                resultSet.getString("recent_dota_match_id"),
                resultSet.getObject("recent_played_at", OffsetDateTime.class),
                resultSet.getObject("best_match_id", UUID.class),
                resultSet.getObject("best_match_game_id", UUID.class),
                resultSet.getString("best_dota_match_id"),
                resultSet.getObject("best_played_at", OffsetDateTime.class),
                decimal(resultSet, "best_kda"));
    }

    private BigDecimal decimal(ResultSet resultSet, String column) throws SQLException {
        BigDecimal value = resultSet.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }

    private record QueryParts(String sql, List<Object> parameters) {
    }
}
