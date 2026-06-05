-- Reset generated integration-test data before reseeding realistic mock data.
-- DO NOT RUN ON PRODUCTION without explicit approval.
-- This script targets known synthetic data markers only:
-- - auth emails under integration.test, test.com, and dotaops.local
-- - generated tournament/team slugs ending in a 12-hex suffix
-- - stable DotaOps demo seed UUIDs
--
-- It intentionally preserves:
-- - real auth accounts such as gmail.com users
-- - Dota hero reference rows
-- - storage buckets/objects
-- - append-only audit_log history

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

create temporary table test_auth_users on commit drop as
select u.id, lower(u.email) as email
from auth.users u
where lower(split_part(u.email, '@', 2)) in (
  'integration.test',
  'test.com',
  'dotaops.local'
);

create temporary table test_auth_sessions on commit drop as
select s.id
from auth.sessions s
where s.user_id in (select id from test_auth_users);

create temporary table seeded_profile_ids (id uuid primary key) on commit drop;

insert into seeded_profile_ids (id)
values
  (pg_temp.demo_uuid('profile:admin')),
  (pg_temp.demo_uuid('profile:organizer')),
  (pg_temp.demo_uuid('profile:player-01')),
  (pg_temp.demo_uuid('profile:player-02')),
  (pg_temp.demo_uuid('profile:player-03')),
  (pg_temp.demo_uuid('profile:player-04')),
  (pg_temp.demo_uuid('profile:player-05')),
  (pg_temp.demo_uuid('profile:player-06')),
  (pg_temp.demo_uuid('profile:player-07')),
  (pg_temp.demo_uuid('profile:player-08')),
  (pg_temp.demo_uuid('profile:player-09')),
  (pg_temp.demo_uuid('profile:player-10')),
  (pg_temp.demo_uuid('profile:player-11')),
  (pg_temp.demo_uuid('profile:player-12')),
  (pg_temp.demo_uuid('profile:player-13')),
  (pg_temp.demo_uuid('profile:player-14')),
  (pg_temp.demo_uuid('profile:player-15')),
  (pg_temp.demo_uuid('profile:player-16')),
  (pg_temp.demo_uuid('profile:player-17')),
  (pg_temp.demo_uuid('profile:player-18')),
  (pg_temp.demo_uuid('profile:player-19')),
  (pg_temp.demo_uuid('profile:player-20')),
  (pg_temp.demo_uuid('profile:player-21')),
  (pg_temp.demo_uuid('profile:player-22')),
  (pg_temp.demo_uuid('profile:player-23')),
  (pg_temp.demo_uuid('profile:player-24')),
  (pg_temp.demo_uuid('profile:player-25')),
  (pg_temp.demo_uuid('profile:player-26')),
  (pg_temp.demo_uuid('profile:player-27')),
  (pg_temp.demo_uuid('profile:player-28')),
  (pg_temp.demo_uuid('profile:player-29')),
  (pg_temp.demo_uuid('profile:player-30'));

create temporary table generated_tournament_ids on commit drop as
select t.id
from public.tournaments t
where t.slug ~ '-[0-9a-f]{12}$'
   or t.id in (
     pg_temp.demo_uuid('tournament:demo-cup'),
     pg_temp.demo_uuid('tournament:open-qualifier')
   );

create temporary table generated_team_ids on commit drop as
select tm.id
from public.teams tm
where tm.slug ~ '-[0-9a-f]{12}$'
   or tm.id in (
     pg_temp.demo_uuid('team:radiant-wolves'),
     pg_temp.demo_uuid('team:dire-ravens'),
     pg_temp.demo_uuid('team:ancient-titans'),
     pg_temp.demo_uuid('team:roshan-hunters'),
     pg_temp.demo_uuid('team:midlane-mages'),
     pg_temp.demo_uuid('team:rune-raiders')
   );

create temporary table generated_profile_ids on commit drop as
select p.id
from public.profiles p
where p.id in (select id from seeded_profile_ids)
   or p.auth_user_id in (select id from test_auth_users)
   or p.nickname like 'demo\_%' escape '\';

delete from public.tournaments
where id in (select id from generated_tournament_ids);

delete from public.teams
where id in (select id from generated_team_ids);

delete from public.profile_external_accounts
where profile_id in (select id from generated_profile_ids)
   or (
     provider = 'email'::public.dotaops_external_account_provider
     and lower(split_part(provider_account_id, '@', 2)) in (
       'integration.test',
       'test.com',
       'dotaops.local'
     )
   );

delete from public.profiles
where id in (select id from generated_profile_ids);

delete from auth.mfa_amr_claims
where session_id in (select id from test_auth_sessions);

delete from auth.refresh_tokens
where session_id in (select id from test_auth_sessions)
   or user_id in (select id::text from test_auth_users);

delete from auth.sessions
where id in (select id from test_auth_sessions);

delete from auth.one_time_tokens
where user_id in (select id from test_auth_users);

delete from auth.identities
where user_id in (select id from test_auth_users);

delete from auth.users
where id in (select id from test_auth_users);

select private.refresh_dotaops_analytics();

commit;
