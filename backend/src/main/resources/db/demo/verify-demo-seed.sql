-- Read-only sanity checks for DotaOps BE/DB-27 demo seed data.

create or replace function pg_temp.demo_uuid(seed text)
returns uuid
language sql
immutable
as $$
  select (
    substr(md5(seed), 1, 8) || '-' ||
    substr(md5(seed), 9, 4) || '-' ||
    substr(md5(seed), 13, 4) || '-' ||
    substr(md5(seed), 17, 4) || '-' ||
    substr(md5(seed), 21, 12)
  )::uuid
$$;

with checks as (
  select
    'demo organizer profile exists' as check_name,
    (select count(*) from public.profiles where id = pg_temp.demo_uuid('profile:organizer')) as actual,
    1 as expected_min
  union all
  select
    'demo profiles exist',
    (select count(*) from public.profiles where nickname like 'demo_%'),
    32
  union all
  select
    'demo teams exist',
    (select count(*) from public.teams where slug in (
      'radiant-wolves',
      'dire-ravens',
      'ancient-titans',
      'roshan-hunters',
      'midlane-mages',
      'rune-raiders'
    )),
    6
  union all
  select
    'dota hero reference catalog exists',
    (select count(*) from public.heroes),
    127
  union all
  select
    'demo public tournaments exist',
    (select count(*) from public.tournaments where slug in (
      'dotaops-demo-cup',
      'dotaops-demo-open-qualifier'
    ) and is_public),
    2
  union all
  select
    'approved demo cup registrations exist',
    (
      select count(*)
      from public.tournament_registrations
      where tournament_id = pg_temp.demo_uuid('tournament:demo-cup')
        and status = 'approved'::public.dotaops_registration_status
    ),
    4
  union all
  select
    'pending registration exists',
    (
      select count(*)
      from public.tournament_registrations
      where status = 'pending'::public.dotaops_registration_status
        and tournament_id in (
          pg_temp.demo_uuid('tournament:demo-cup'),
          pg_temp.demo_uuid('tournament:open-qualifier')
        )
    ),
    2
  union all
  select
    'playoff bracket matches exist',
    (
      select count(*)
      from public.matches
      where tournament_id = pg_temp.demo_uuid('tournament:demo-cup')
        and stage_name = 'Playoffs'
    ),
    3
  union all
  select
    'completed demo matches exist',
    (
      select count(*)
      from public.matches
      where tournament_id = pg_temp.demo_uuid('tournament:demo-cup')
        and status = 'finished'::public.dotaops_match_status
    ),
    5
  union all
  select
    'match games exist',
    (
      select count(*)
      from public.match_games mg
      join public.matches m on m.id = mg.match_id
      where m.tournament_id = pg_temp.demo_uuid('tournament:demo-cup')
    ),
    12
  union all
  select
    'match players exist',
    (
      select count(*)
      from public.match_players mp
      join public.matches m on m.id = mp.match_id
      where m.tournament_id = pg_temp.demo_uuid('tournament:demo-cup')
    ),
    120
  union all
  select
    'analytics player data exists',
    (
      select count(distinct mp.profile_id)
      from public.match_players mp
      join public.matches m on m.id = mp.match_id
      where m.tournament_id = pg_temp.demo_uuid('tournament:demo-cup')
        and mp.profile_id is not null
    ),
    20
)
select
  check_name,
  actual,
  expected_min,
  actual >= expected_min as ok
from checks
order by check_name;
