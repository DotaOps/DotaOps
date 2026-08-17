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

with demo_login_accounts as (
  select
    'organizer'::text as profile_key,
    'demo.organizer@dotaops.local'::text as email
  union all
  select
    'player-' || lpad(player_number::text, 2, '0'),
    'demo.player' || player_number || '@dotaops.local'
  from generate_series(1, 30) as demo_players(player_number)
),
required_demo_login_accounts(profile_key, email, display_name) as (
  values
    ('organizer', 'demo.organizer@dotaops.local', 'Matej Novak'),
    ('player-01', 'demo.player1@dotaops.local', 'Luka Kranjc'),
    ('player-07', 'demo.player7@dotaops.local', 'Mia Horvat'),
    ('player-11', 'demo.player11@dotaops.local', 'Felix Berger'),
    ('player-16', 'demo.player16@dotaops.local', 'Jonas Keller')
),
checks as (
  select
    'demo organizer profile exists' as check_name,
    (select count(*) from public.profiles where id = pg_temp.demo_uuid('profile:organizer')) as actual,
    1 as expected_min
  union all
  select
    'demo auth users exist',
    (
      select count(distinct u.id)
      from demo_login_accounts a
      join auth.users u on lower(u.email) = a.email
      where u.encrypted_password is not null
    ),
    31
  union all
  select
    'demo auth users are email confirmed',
    (
      select count(distinct u.id)
      from demo_login_accounts a
      join auth.users u on lower(u.email) = a.email
      where u.email_confirmed_at is not null
        and u.confirmed_at is not null
    ),
    31
  union all
  select
    'demo auth users have gotrue token defaults',
    (
      select count(distinct u.id)
      from demo_login_accounts a
      join auth.users u on lower(u.email) = a.email
      where coalesce(u.confirmation_token, '') = ''
        and coalesce(u.recovery_token, '') = ''
        and coalesce(u.email_change_token_new, '') = ''
        and coalesce(u.email_change, '') = ''
    ),
    31
  union all
  select
    'demo auth identities exist',
    (
      select count(distinct i.user_id)
      from demo_login_accounts a
      join auth.users u on lower(u.email) = a.email
      join auth.identities i on i.user_id = u.id
      where i.provider = 'email'
        and i.provider_id = u.id::text
        and i.identity_data->>'email' = u.email
    ),
    31
  union all
  select
    'demo profiles linked to auth users',
    (
      select count(*)
      from demo_login_accounts a
      join auth.users u on lower(u.email) = a.email
      join public.profiles p
        on p.id = pg_temp.demo_uuid('profile:' || a.profile_key)
       and p.auth_user_id = u.id
    ),
    31
  union all
  select
    'required demo login profiles map to seeded profiles',
    (
      select count(*)
      from required_demo_login_accounts a
      join auth.users u on lower(u.email) = a.email
      join public.profiles p
        on p.id = pg_temp.demo_uuid('profile:' || a.profile_key)
       and p.auth_user_id = u.id
       and p.display_name = a.display_name
    ),
    5
  union all
  select
    'realistic mock profiles exist',
    (
      select count(*)
      from public.profiles
      where id in (
        select pg_temp.demo_uuid('profile:player-' || lpad(player_number::text, 2, '0'))
        from generate_series(1, 30) as demo_players(player_number)
        union all
        select pg_temp.demo_uuid('profile:admin')
        union all
        select pg_temp.demo_uuid('profile:organizer')
      )
    ),
    32
  union all
  select
    'realistic mock teams exist',
    (select count(*) from public.teams where slug in (
      'ljubljana-wardens',
      'adriatic-ravens',
      'alpine-aegis',
      'roshan-hunters-club',
      'rome-midlane',
      'danube-raiders'
    )),
    6
  union all
  select
    'dota hero reference catalog exists',
    (select count(*) from public.heroes),
    127
  union all
  select
    'realistic mock public tournaments exist',
    (select count(*) from public.tournaments where slug in (
      'ljubljana-summer-circuit-2026',
      'adriatic-open-qualifier-2026'
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
