-- Reset DotaOps demo seed data for local/dev/demo databases only.
-- DO NOT RUN ON PRODUCTION without explicit approval.
-- This script deletes only stable BE/DB-27 demo records and avoids broad table wipes.

begin;

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

create temporary table demo_auth_accounts (
  email text primary key
) on commit drop;

insert into demo_auth_accounts (email)
values ('demo.organizer@dotaops.local');

insert into demo_auth_accounts (email)
select 'demo.player' || player_number || '@dotaops.local'
from generate_series(1, 30) as demo_players(player_number);

delete from public.tournaments
where id in (
  pg_temp.demo_uuid('tournament:demo-cup'),
  pg_temp.demo_uuid('tournament:open-qualifier')
);

delete from public.teams
where id in (
  pg_temp.demo_uuid('team:radiant-wolves'),
  pg_temp.demo_uuid('team:dire-ravens'),
  pg_temp.demo_uuid('team:ancient-titans'),
  pg_temp.demo_uuid('team:roshan-hunters'),
  pg_temp.demo_uuid('team:midlane-mages'),
  pg_temp.demo_uuid('team:rune-raiders')
);

delete from public.profile_external_accounts
where provider = 'email'::public.dotaops_external_account_provider
  and provider_account_id like 'demo.%@dotaops.local';

delete from public.profiles p
using auth.users u, demo_auth_accounts a
where p.auth_user_id = u.id
  and lower(u.email) = a.email;

delete from public.profiles
where id in (
  pg_temp.demo_uuid('profile:admin'),
  pg_temp.demo_uuid('profile:organizer'),
  pg_temp.demo_uuid('profile:player-01'),
  pg_temp.demo_uuid('profile:player-02'),
  pg_temp.demo_uuid('profile:player-03'),
  pg_temp.demo_uuid('profile:player-04'),
  pg_temp.demo_uuid('profile:player-05'),
  pg_temp.demo_uuid('profile:player-06'),
  pg_temp.demo_uuid('profile:player-07'),
  pg_temp.demo_uuid('profile:player-08'),
  pg_temp.demo_uuid('profile:player-09'),
  pg_temp.demo_uuid('profile:player-10'),
  pg_temp.demo_uuid('profile:player-11'),
  pg_temp.demo_uuid('profile:player-12'),
  pg_temp.demo_uuid('profile:player-13'),
  pg_temp.demo_uuid('profile:player-14'),
  pg_temp.demo_uuid('profile:player-15'),
  pg_temp.demo_uuid('profile:player-16'),
  pg_temp.demo_uuid('profile:player-17'),
  pg_temp.demo_uuid('profile:player-18'),
  pg_temp.demo_uuid('profile:player-19'),
  pg_temp.demo_uuid('profile:player-20'),
  pg_temp.demo_uuid('profile:player-21'),
  pg_temp.demo_uuid('profile:player-22'),
  pg_temp.demo_uuid('profile:player-23'),
  pg_temp.demo_uuid('profile:player-24'),
  pg_temp.demo_uuid('profile:player-25'),
  pg_temp.demo_uuid('profile:player-26'),
  pg_temp.demo_uuid('profile:player-27'),
  pg_temp.demo_uuid('profile:player-28'),
  pg_temp.demo_uuid('profile:player-29'),
  pg_temp.demo_uuid('profile:player-30')
);

delete from auth.identities i
using auth.users u, demo_auth_accounts a
where i.user_id = u.id
  and lower(u.email) = a.email;

delete from auth.users u
using demo_auth_accounts a
where lower(u.email) = a.email;

select private.refresh_dotaops_analytics();

commit;
