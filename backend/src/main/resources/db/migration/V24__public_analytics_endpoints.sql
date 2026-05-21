create or replace function private.refresh_dotaops_analytics()
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  refresh materialized view public.mv_player_metrics;
  refresh materialized view public.mv_team_metrics;
  refresh materialized view public.mv_hero_metrics;
  refresh materialized view public.mv_tournament_metrics;
end;
$$;

create index if not exists match_games_import_status_idx
  on public.match_games(import_status);

create index if not exists match_players_profile_id_idx
  on public.match_players(profile_id)
  where profile_id is not null;

create index if not exists match_players_team_id_idx
  on public.match_players(team_id)
  where team_id is not null;

create index if not exists match_players_hero_id_idx
  on public.match_players(hero_id)
  where hero_id is not null;

drop view if exists public.v_player_metrics;
create view public.v_player_metrics
with (security_invoker = true)
as
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
  round(((count(*) filter (where mp.is_winner is true))::numeric / greatest(count(*), 1)) * 100, 2) as win_rate,
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
where t.is_public
  and mp.profile_id is not null
group by p.id, p.display_name, p.nickname, mp.team_id, tm.name, m.tournament_id, t.title;

drop view if exists public.v_team_metrics;
create view public.v_team_metrics
with (security_invoker = true)
as
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
    / greatest(count(distinct coalesce(mp.match_game_id::text, mp.match_id::text)), 1)) * 100, 2) as win_rate,
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
where t.is_public
  and mp.team_id is not null
group by tm.id, tm.name, m.tournament_id, t.title;

drop view if exists public.v_hero_metrics;
create view public.v_hero_metrics
with (security_invoker = true)
as
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
  round(((count(*) filter (where mp.is_winner is true))::numeric / greatest(count(*), 1)) * 100, 2) as win_rate,
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
where t.is_public
  and mp.hero_id is not null
group by h.id, h.dota_hero_id, h.name, h.localized_name, h.image_url, h.icon_url, m.tournament_id, t.title;

drop view if exists public.v_tournament_metrics;
create view public.v_tournament_metrics
with (security_invoker = true)
as
with public_players as (
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
  where t.is_public
),
game_durations as (
  select
    tournament_id,
    game_key,
    max(duration_seconds) as duration_seconds
  from public_players
  group by tournament_id, game_key
)
select
  pp.tournament_id,
  pp.tournament_name,
  count(distinct pp.game_key)::integer as games_played,
  count(distinct pp.team_id)::integer as teams_count,
  count(distinct pp.profile_id)::integer as players_count,
  count(distinct pp.hero_id)::integer as heroes_picked_count,
  round(avg(gd.duration_seconds))::integer as avg_duration_seconds,
  coalesce(sum(pp.kills), 0)::integer as total_kills,
  coalesce(sum(pp.deaths), 0)::integer as total_deaths,
  coalesce(sum(pp.assists), 0)::integer as total_assists,
  round(coalesce(sum(pp.kills), 0)::numeric / greatest(count(distinct pp.game_key), 1), 2) as avg_kills_per_game,
  round((coalesce(sum(pp.kills), 0) + coalesce(sum(pp.assists), 0))::numeric
    / greatest(coalesce(sum(pp.deaths), 0), 1), 2) as avg_kda
from public_players pp
left join game_durations gd
  on gd.tournament_id = pp.tournament_id
 and gd.game_key = pp.game_key
group by pp.tournament_id, pp.tournament_name;

grant select on
  public.v_player_metrics,
  public.v_team_metrics,
  public.v_hero_metrics,
  public.v_tournament_metrics
to anon, authenticated, service_role;

grant execute on function private.refresh_dotaops_analytics() to service_role;

comment on function private.refresh_dotaops_analytics() is
  'Refreshes DotaOps analytics materialized views. Called by backend admin/async analytics refresh flows.';
