package si.um.feri.dotaops.backend.analytics.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import si.um.feri.dotaops.backend.analytics.domain.AnalyticsFilters;
import si.um.feri.dotaops.backend.analytics.domain.HeroMetrics;
import si.um.feri.dotaops.backend.analytics.domain.PickedHeroMetrics;
import si.um.feri.dotaops.backend.analytics.domain.PlayerMetrics;
import si.um.feri.dotaops.backend.analytics.domain.TeamMetrics;
import si.um.feri.dotaops.backend.analytics.domain.TournamentMetrics;

@Repository
public class AnalyticsRepository {

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PlayerMetrics> findPlayerMetrics(AnalyticsFilters filters) {
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
                where t.is_public = true
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
                where t.is_public = true
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
                where t.is_public = true
                  and mp.hero_id is not null
                """ + queryParts.sql() + """
                group by h.id, h.dota_hero_id, h.name, h.localized_name, h.image_url, h.icon_url, m.tournament_id, t.title
                order by games_played desc, win_rate desc, h.localized_name asc
                limit ?
                """,
                this::mapHeroMetrics,
                parameters.toArray());
    }

    public List<TournamentMetrics> findTournamentMetrics(AnalyticsFilters filters) {
        QueryParts queryParts = filteredWhere(filters, "mp", "m");
        List<Object> parameters = new ArrayList<>(queryParts.parameters());
        parameters.add(filters.limit());

        List<TournamentMetrics> metrics = jdbcTemplate.query(
                tournamentMetricsSql(queryParts.sql()) + """
                order by player_agg.games_played desc, player_agg.tournament_name asc
                limit ?
                """,
                this::mapTournamentMetricsWithoutPickedHeroes,
                parameters.toArray());

        return metrics.stream()
                .map(metric -> withPickedHeroes(metric, filters.withTournamentId(metric.tournamentId())))
                .toList();
    }

    public Optional<TournamentMetrics> findTournamentMetricsById(UUID tournamentId) {
        AnalyticsFilters filters = new AnalyticsFilters(tournamentId, null, null, null, 1);
        QueryParts queryParts = filteredWhere(filters, "mp", "m");

        return jdbcTemplate.query(
                        tournamentMetricsSql(queryParts.sql()),
                        this::mapTournamentMetricsWithoutPickedHeroes,
                        queryParts.parameters().toArray())
                .stream()
                .findFirst()
                .map(metric -> withPickedHeroes(metric, filters));
    }

    private TournamentMetrics withPickedHeroes(TournamentMetrics metrics, AnalyticsFilters filters) {
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
                findMostPickedHeroes(filters));
    }

    private List<PickedHeroMetrics> findMostPickedHeroes(AnalyticsFilters filters) {
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
                where t.is_public = true
                  and mp.hero_id is not null
                """ + queryParts.sql() + """
                group by h.id, h.dota_hero_id, h.localized_name, h.image_url, h.icon_url
                order by picks desc, h.localized_name asc
                limit ?
                """,
                this::mapPickedHeroMetrics,
                parameters.toArray());
    }

    private String tournamentMetricsSql(String filteredWhere) {
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
                  where t.is_public = true
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

    private QueryParts filteredWhere(AnalyticsFilters filters, String playerAlias, String matchAlias) {
        List<String> clauses = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();

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

        if (clauses.isEmpty()) {
            return new QueryParts("", parameters);
        }

        return new QueryParts(" and " + String.join(" and ", clauses) + "\n", parameters);
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

    private BigDecimal decimal(ResultSet resultSet, String column) throws SQLException {
        BigDecimal value = resultSet.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }

    private record QueryParts(String sql, List<Object> parameters) {
    }
}
