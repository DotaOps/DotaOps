-- DotaOps demo seed for local/dev/demo databases only.
-- DO NOT RUN ON PRODUCTION without explicit approval.
-- This file contains synthetic demo data under @dotaops.local and no secrets.
-- Preferred entry point: scripts/seed-demo.ps1 -ConfirmDemoSeed

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

create temporary table demo_profile_seed (
  profile_key text primary key,
  nickname text not null,
  display_name text not null,
  role text not null,
  email text not null,
  team_key text,
  slot_order integer,
  member_role text,
  country_code char(2),
  dota_account_id bigint
) on commit drop;

insert into demo_profile_seed (
  profile_key,
  nickname,
  display_name,
  role,
  email,
  team_key,
  slot_order,
  member_role,
  country_code,
  dota_account_id
)
values
  ('admin', 'demo_admin', 'Demo Admin', 'admin', 'demo.admin@dotaops.local', null, null, null, 'SI', null),
  ('organizer', 'demo_organizer', 'Demo Organizer', 'organizer', 'demo.organizer@dotaops.local', null, null, null, 'SI', null),
  ('player-01', 'demo_player_01', 'Aegis Ace', 'player', 'demo.player1@dotaops.local', 'radiant-wolves', 1, 'carry', 'SI', 900000001),
  ('player-02', 'demo_player_02', 'Mid Signal', 'player', 'demo.player2@dotaops.local', 'radiant-wolves', 2, 'mid', 'SI', 900000002),
  ('player-03', 'demo_player_03', 'Offlane Pulse', 'player', 'demo.player3@dotaops.local', 'radiant-wolves', 3, 'offlane', 'SI', 900000003),
  ('player-04', 'demo_player_04', 'Ward Runner', 'player', 'demo.player4@dotaops.local', 'radiant-wolves', 4, 'support', 'SI', 900000004),
  ('player-05', 'demo_player_05', 'Smoke Caller', 'player', 'demo.player5@dotaops.local', 'radiant-wolves', 5, 'support', 'SI', 900000005),
  ('player-06', 'demo_player_06', 'Dire Edge', 'player', 'demo.player6@dotaops.local', 'dire-ravens', 1, 'carry', 'HR', 900000006),
  ('player-07', 'demo_player_07', 'Risky Mid', 'player', 'demo.player7@dotaops.local', 'dire-ravens', 2, 'mid', 'HR', 900000007),
  ('player-08', 'demo_player_08', 'Lane Anchor', 'player', 'demo.player8@dotaops.local', 'dire-ravens', 3, 'offlane', 'HR', 900000008),
  ('player-09', 'demo_player_09', 'Rune Scout', 'player', 'demo.player9@dotaops.local', 'dire-ravens', 4, 'roamer', 'HR', 900000009),
  ('player-10', 'demo_player_10', 'Crow Support', 'player', 'demo.player10@dotaops.local', 'dire-ravens', 5, 'support', 'HR', 900000010),
  ('player-11', 'demo_player_11', 'Titan Carry', 'player', 'demo.player11@dotaops.local', 'ancient-titans', 1, 'carry', 'AT', 900000011),
  ('player-12', 'demo_player_12', 'Titan Mid', 'player', 'demo.player12@dotaops.local', 'ancient-titans', 2, 'mid', 'AT', 900000012),
  ('player-13', 'demo_player_13', 'Titan Wall', 'player', 'demo.player13@dotaops.local', 'ancient-titans', 3, 'offlane', 'AT', 900000013),
  ('player-14', 'demo_player_14', 'Titan Roam', 'player', 'demo.player14@dotaops.local', 'ancient-titans', 4, 'roamer', 'AT', 900000014),
  ('player-15', 'demo_player_15', 'Titan Ward', 'player', 'demo.player15@dotaops.local', 'ancient-titans', 5, 'support', 'AT', 900000015),
  ('player-16', 'demo_player_16', 'Roshan Core', 'player', 'demo.player16@dotaops.local', 'roshan-hunters', 1, 'carry', 'DE', 900000016),
  ('player-17', 'demo_player_17', 'Pit Mid', 'player', 'demo.player17@dotaops.local', 'roshan-hunters', 2, 'mid', 'DE', 900000017),
  ('player-18', 'demo_player_18', 'Shard Offlane', 'player', 'demo.player18@dotaops.local', 'roshan-hunters', 3, 'offlane', 'DE', 900000018),
  ('player-19', 'demo_player_19', 'Aegis Support', 'player', 'demo.player19@dotaops.local', 'roshan-hunters', 4, 'support', 'DE', 900000019),
  ('player-20', 'demo_player_20', 'Cheese Guard', 'player', 'demo.player20@dotaops.local', 'roshan-hunters', 5, 'support', 'DE', 900000020),
  ('player-21', 'demo_player_21', 'Mage Carry', 'player', 'demo.player21@dotaops.local', 'midlane-mages', 1, 'carry', 'IT', 900000021),
  ('player-22', 'demo_player_22', 'Arcane Mid', 'player', 'demo.player22@dotaops.local', 'midlane-mages', 2, 'mid', 'IT', 900000022),
  ('player-23', 'demo_player_23', 'Glyph Offlane', 'player', 'demo.player23@dotaops.local', 'midlane-mages', 3, 'offlane', 'IT', 900000023),
  ('player-24', 'demo_player_24', 'Mana Scout', 'player', 'demo.player24@dotaops.local', 'midlane-mages', 4, 'roamer', 'IT', 900000024),
  ('player-25', 'demo_player_25', 'Scroll Support', 'player', 'demo.player25@dotaops.local', 'midlane-mages', 5, 'support', 'IT', 900000025),
  ('player-26', 'demo_player_26', 'Rune Carry', 'player', 'demo.player26@dotaops.local', 'rune-raiders', 1, 'carry', 'HU', 900000026),
  ('player-27', 'demo_player_27', 'Water Rune', 'player', 'demo.player27@dotaops.local', 'rune-raiders', 2, 'mid', 'HU', 900000027),
  ('player-28', 'demo_player_28', 'Power Rune', 'player', 'demo.player28@dotaops.local', 'rune-raiders', 3, 'offlane', 'HU', 900000028),
  ('player-29', 'demo_player_29', 'Bounty Scout', 'player', 'demo.player29@dotaops.local', 'rune-raiders', 4, 'roamer', 'HU', 900000029),
  ('player-30', 'demo_player_30', 'Lotus Support', 'player', 'demo.player30@dotaops.local', 'rune-raiders', 5, 'support', 'HU', 900000030);

insert into public.profiles (
  id,
  auth_user_id,
  nickname,
  display_name,
  role,
  avatar_url,
  bio,
  country_code
)
select
  pg_temp.demo_uuid('profile:' || profile_key),
  null,
  nickname,
  display_name,
  role::public.dotaops_user_role,
  'https://example.invalid/dotaops/demo/avatars/' || profile_key || '.png',
  'Synthetic DotaOps demo profile for local/dev/demo environments.',
  country_code
from demo_profile_seed
on conflict (id) do update
set nickname = excluded.nickname,
    display_name = excluded.display_name,
    role = excluded.role,
    avatar_url = excluded.avatar_url,
    bio = excluded.bio,
    country_code = excluded.country_code,
    updated_at = now();

insert into public.profile_external_accounts (
  id,
  profile_id,
  provider,
  provider_account_id,
  display_name,
  is_primary,
  verified_at,
  metadata
)
select
  pg_temp.demo_uuid('email-account:' || profile_key),
  pg_temp.demo_uuid('profile:' || profile_key),
  'email'::public.dotaops_external_account_provider,
  email,
  display_name,
  true,
  timestamptz '2026-05-01 00:00:00+00',
  jsonb_build_object('demo', true)
from demo_profile_seed
on conflict (provider, provider_account_id) do update
set profile_id = excluded.profile_id,
    display_name = excluded.display_name,
    is_primary = excluded.is_primary,
    verified_at = excluded.verified_at,
    metadata = excluded.metadata,
    updated_at = now();

select set_config('request.dotaops.profile_id', pg_temp.demo_uuid('profile:organizer')::text, true);

create temporary table demo_team_seed (
  team_key text primary key,
  name text not null,
  tag text not null,
  slug text not null,
  captain_key text not null,
  region text not null,
  seed_number integer,
  registration_status text,
  registration_message text
) on commit drop;

insert into demo_team_seed (
  team_key,
  name,
  tag,
  slug,
  captain_key,
  region,
  seed_number,
  registration_status,
  registration_message
)
values
  ('radiant-wolves', 'Radiant Wolves', 'RW', 'radiant-wolves', 'player-01', 'EU Central', 1, 'approved', 'Ready for a full best-of-three demo flow.'),
  ('dire-ravens', 'Dire Ravens', 'DR', 'dire-ravens', 'player-06', 'Adriatic', 4, 'approved', 'Roster locked and available for analytics comparison.'),
  ('ancient-titans', 'Ancient Titans', 'AT', 'ancient-titans', 'player-11', 'Alps', 2, 'approved', 'Experienced demo lineup with strong teamfight metrics.'),
  ('roshan-hunters', 'Roshan Hunters', 'RH', 'roshan-hunters', 'player-16', 'DACH', 3, 'approved', 'Objective-focused roster for public bracket examples.'),
  ('midlane-mages', 'Midlane Mages', 'MM', 'midlane-mages', 'player-21', 'Italy', null, 'pending', 'Pending demo registration for organizer review.'),
  ('rune-raiders', 'Rune Raiders', 'RR', 'rune-raiders', 'player-26', 'Pannonia', null, 'rejected', 'Rejected demo registration for review state coverage.');

insert into public.teams (
  id,
  name,
  tag,
  slug,
  captain_profile_id,
  region,
  logo_url,
  banner_url,
  description,
  created_by,
  disbanded_at
)
select
  pg_temp.demo_uuid('team:' || team_key),
  name,
  tag,
  slug,
  pg_temp.demo_uuid('profile:' || captain_key),
  region,
  'https://example.invalid/dotaops/demo/team-assets/' || slug || '-logo.png',
  'https://example.invalid/dotaops/demo/team-assets/' || slug || '-banner.png',
  'Synthetic demo team seeded for DotaOps public, team and analytics flows.',
  null,
  null
from demo_team_seed
on conflict (id) do update
set name = excluded.name,
    tag = excluded.tag,
    slug = excluded.slug,
    captain_profile_id = excluded.captain_profile_id,
    region = excluded.region,
    logo_url = excluded.logo_url,
    banner_url = excluded.banner_url,
    description = excluded.description,
    disbanded_at = null,
    updated_at = now();

insert into public.team_members (
  id,
  team_id,
  profile_id,
  member_role,
  is_active,
  joined_at,
  left_at
)
select
  pg_temp.demo_uuid('team-member:' || profile_key),
  pg_temp.demo_uuid('team:' || team_key),
  pg_temp.demo_uuid('profile:' || profile_key),
  member_role::public.dotaops_team_member_role,
  true,
  timestamptz '2026-05-05 12:00:00+00' + ((slot_order || ' hours')::interval),
  null
from demo_profile_seed
where team_key is not null
on conflict (id) do update
set team_id = excluded.team_id,
    profile_id = excluded.profile_id,
    member_role = excluded.member_role,
    is_active = true,
    joined_at = excluded.joined_at,
    left_at = null,
    updated_at = now();

insert into public.heroes (
  id,
  dota_hero_id,
  name,
  localized_name,
  primary_attr,
  attack_type,
  roles,
  slug,
  image_url,
  icon_url
)
values
  (pg_temp.demo_uuid('hero:1'), 1, 'npc_dota_hero_antimage', 'Anti-Mage', 'agi', 'Melee', array['Carry', 'Escape'], 'anti-mage', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/antimage.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/antimage.png'),
  (pg_temp.demo_uuid('hero:2'), 2, 'npc_dota_hero_axe', 'Axe', 'str', 'Melee', array['Initiator', 'Durable'], 'axe', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/axe.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/axe.png'),
  (pg_temp.demo_uuid('hero:5'), 5, 'npc_dota_hero_crystal_maiden', 'Crystal Maiden', 'int', 'Ranged', array['Support', 'Disabler'], 'crystal-maiden', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/crystal_maiden.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/crystal_maiden.png'),
  (pg_temp.demo_uuid('hero:6'), 6, 'npc_dota_hero_drow_ranger', 'Drow Ranger', 'agi', 'Ranged', array['Carry', 'Disabler'], 'drow-ranger', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/drow_ranger.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/drow_ranger.png'),
  (pg_temp.demo_uuid('hero:8'), 8, 'npc_dota_hero_juggernaut', 'Juggernaut', 'agi', 'Melee', array['Carry', 'Pusher'], 'juggernaut', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/juggernaut.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/juggernaut.png'),
  (pg_temp.demo_uuid('hero:11'), 11, 'npc_dota_hero_nevermore', 'Shadow Fiend', 'agi', 'Ranged', array['Carry', 'Nuker'], 'shadow-fiend', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/nevermore.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/nevermore.png'),
  (pg_temp.demo_uuid('hero:14'), 14, 'npc_dota_hero_pudge', 'Pudge', 'str', 'Melee', array['Disabler', 'Durable'], 'pudge', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/pudge.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/pudge.png'),
  (pg_temp.demo_uuid('hero:25'), 25, 'npc_dota_hero_lina', 'Lina', 'int', 'Ranged', array['Support', 'Carry', 'Nuker'], 'lina', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/lina.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/lina.png'),
  (pg_temp.demo_uuid('hero:74'), 74, 'npc_dota_hero_invoker', 'Invoker', 'int', 'Ranged', array['Carry', 'Nuker', 'Disabler'], 'invoker', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/invoker.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/invoker.png'),
  (pg_temp.demo_uuid('hero:86'), 86, 'npc_dota_hero_rubick', 'Rubick', 'int', 'Ranged', array['Support', 'Disabler', 'Nuker'], 'rubick', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/rubick.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/rubick.png')
on conflict (dota_hero_id) do update
set name = excluded.name,
    localized_name = excluded.localized_name,
    primary_attr = excluded.primary_attr,
    attack_type = excluded.attack_type,
    roles = excluded.roles,
    slug = excluded.slug,
    image_url = excluded.image_url,
    icon_url = excluded.icon_url,
    updated_at = now();

create temporary table demo_tournament_seed (
  tournament_key text primary key,
  slug text not null,
  title text not null,
  status text not null,
  format text not null,
  starts_at timestamptz not null,
  ends_at timestamptz,
  registration_opens_at timestamptz,
  registration_closes_at timestamptz,
  published_at timestamptz,
  is_public boolean not null,
  max_teams integer not null,
  description text,
  rules text,
  prize_pool text,
  settings jsonb not null
) on commit drop;

insert into demo_tournament_seed (
  tournament_key,
  slug,
  title,
  status,
  format,
  starts_at,
  ends_at,
  registration_opens_at,
  registration_closes_at,
  published_at,
  is_public,
  max_teams,
  description,
  rules,
  prize_pool,
  settings
)
values
  (
    'demo-cup',
    'dotaops-demo-cup',
    'DotaOps Demo Cup',
    'live',
    'groups_playoff',
    timestamptz '2026-06-01 12:00:00+00',
    timestamptz '2026-06-07 20:00:00+00',
    timestamptz '2026-05-01 08:00:00+00',
    timestamptz '2026-05-25 20:00:00+00',
    timestamptz '2026-05-26 10:00:00+00',
    true,
    8,
    'End-to-end DotaOps demo tournament with registrations, groups, bracket, match games and analytics.',
    'Synthetic demo rules. No real players or real tournament results are represented.',
    'Demo Aegis + bragging rights',
    '{"teamSize":5,"minTeams":4,"maxTeams":8,"bestOf":3,"format":"groups_playoff","allowSubstitutes":true,"checkInEnabled":true}'::jsonb
  ),
  (
    'open-qualifier',
    'dotaops-demo-open-qualifier',
    'DotaOps Demo Open Qualifier',
    'registration',
    'single_elimination',
    timestamptz '2026-06-15 14:00:00+00',
    timestamptz '2026-06-16 20:00:00+00',
    timestamptz '2026-06-03 08:00:00+00',
    timestamptz '2026-06-12 20:00:00+00',
    timestamptz '2026-06-03 09:00:00+00',
    true,
    8,
    'Registration-open demo tournament for organizer workflow and public catalogue states.',
    'Synthetic qualifier rules for demo only.',
    'Demo qualifier slot',
    '{"teamSize":5,"minTeams":2,"maxTeams":8,"bestOf":3,"format":"single_elimination","allowSubstitutes":true,"checkInEnabled":false}'::jsonb
  );

insert into public.tournaments (
  id,
  slug,
  title,
  status,
  format,
  organizer_profile_id,
  description,
  rules,
  prize_pool,
  max_teams,
  starts_at,
  ends_at,
  registration_opens_at,
  registration_closes_at,
  is_public,
  created_by,
  timezone,
  check_in_opens_at,
  check_in_closes_at,
  published_at,
  settings
)
select
  pg_temp.demo_uuid('tournament:' || tournament_key),
  slug,
  title,
  status::public.dotaops_tournament_status,
  format::public.dotaops_tournament_format,
  pg_temp.demo_uuid('profile:organizer'),
  description,
  rules,
  prize_pool,
  max_teams,
  starts_at,
  ends_at,
  registration_opens_at,
  registration_closes_at,
  is_public,
  null,
  'Europe/Ljubljana',
  starts_at - interval '2 hours',
  starts_at - interval '30 minutes',
  published_at,
  settings
from demo_tournament_seed
on conflict (id) do update
set slug = excluded.slug,
    title = excluded.title,
    status = excluded.status,
    format = excluded.format,
    organizer_profile_id = excluded.organizer_profile_id,
    description = excluded.description,
    rules = excluded.rules,
    prize_pool = excluded.prize_pool,
    max_teams = excluded.max_teams,
    starts_at = excluded.starts_at,
    ends_at = excluded.ends_at,
    registration_opens_at = excluded.registration_opens_at,
    registration_closes_at = excluded.registration_closes_at,
    is_public = excluded.is_public,
    timezone = excluded.timezone,
    check_in_opens_at = excluded.check_in_opens_at,
    check_in_closes_at = excluded.check_in_closes_at,
    published_at = excluded.published_at,
    settings = excluded.settings,
    updated_at = now();

insert into public.tournament_staff (
  id,
  tournament_id,
  profile_id,
  staff_role
)
select
  pg_temp.demo_uuid('tournament-staff:' || tournament_key || ':organizer'),
  pg_temp.demo_uuid('tournament:' || tournament_key),
  pg_temp.demo_uuid('profile:organizer'),
  'owner'::public.dotaops_tournament_staff_role
from demo_tournament_seed
on conflict (tournament_id, profile_id) do update
set staff_role = excluded.staff_role,
    updated_at = now();

insert into public.tournament_registrations (
  id,
  tournament_id,
  team_id,
  captain_profile_id,
  status,
  message,
  reviewed_by,
  reviewed_at,
  seed_number,
  checked_in_at,
  contact_email,
  metadata
)
select
  pg_temp.demo_uuid('registration:demo-cup:' || team_key),
  pg_temp.demo_uuid('tournament:demo-cup'),
  pg_temp.demo_uuid('team:' || team_key),
  pg_temp.demo_uuid('profile:' || captain_key),
  registration_status::public.dotaops_registration_status,
  registration_message,
  case when registration_status in ('approved', 'rejected', 'waitlisted') then pg_temp.demo_uuid('profile:organizer') else null end,
  case when registration_status in ('approved', 'rejected', 'waitlisted') then timestamptz '2026-05-26 11:00:00+00' else null end,
  seed_number,
  case when registration_status = 'approved' then timestamptz '2026-06-01 10:45:00+00' else null end,
  replace(team_key, '-', '.') || '@dotaops.local',
  jsonb_build_object('demo', true, 'source', 'BE/DB-27')
from demo_team_seed
on conflict (tournament_id, team_id) do update
set captain_profile_id = excluded.captain_profile_id,
    status = excluded.status,
    message = excluded.message,
    reviewed_by = excluded.reviewed_by,
    reviewed_at = excluded.reviewed_at,
    seed_number = excluded.seed_number,
    checked_in_at = excluded.checked_in_at,
    contact_email = excluded.contact_email,
    metadata = excluded.metadata,
    updated_at = now();

insert into public.tournament_registrations (
  id,
  tournament_id,
  team_id,
  captain_profile_id,
  status,
  message,
  contact_email,
  metadata
)
select
  pg_temp.demo_uuid('registration:open-qualifier:rune-raiders'),
  pg_temp.demo_uuid('tournament:open-qualifier'),
  pg_temp.demo_uuid('team:rune-raiders'),
  pg_temp.demo_uuid('profile:player-26'),
  'pending'::public.dotaops_registration_status,
  'Pending qualifier entry for demo organizer dashboard counts.',
  'rune.raiders@dotaops.local',
  jsonb_build_object('demo', true, 'source', 'BE/DB-27')
on conflict (tournament_id, team_id) do update
set captain_profile_id = excluded.captain_profile_id,
    status = excluded.status,
    message = excluded.message,
    contact_email = excluded.contact_email,
    metadata = excluded.metadata,
    reviewed_by = null,
    reviewed_at = null,
    seed_number = null,
    checked_in_at = null,
    updated_at = now();

insert into public.tournament_registration_members (
  id,
  registration_id,
  profile_id,
  team_member_id,
  member_role,
  is_starter
)
select
  pg_temp.demo_uuid('registration-member:demo-cup:' || p.profile_key),
  pg_temp.demo_uuid('registration:demo-cup:' || p.team_key),
  pg_temp.demo_uuid('profile:' || p.profile_key),
  pg_temp.demo_uuid('team-member:' || p.profile_key),
  p.member_role::public.dotaops_team_member_role,
  true
from demo_profile_seed p
where p.team_key is not null
on conflict (id) do update
set registration_id = excluded.registration_id,
    profile_id = excluded.profile_id,
    team_member_id = excluded.team_member_id,
    member_role = excluded.member_role,
    is_starter = true,
    manual_player_id = null,
    manual_display_name = null,
    manual_nickname = null,
    manual_note = null,
    updated_at = now();

insert into public.tournament_registration_members (
  id,
  registration_id,
  profile_id,
  team_member_id,
  member_role,
  is_starter
)
select
  pg_temp.demo_uuid('registration-member:open-qualifier:' || p.profile_key),
  pg_temp.demo_uuid('registration:open-qualifier:rune-raiders'),
  pg_temp.demo_uuid('profile:' || p.profile_key),
  pg_temp.demo_uuid('team-member:' || p.profile_key),
  p.member_role::public.dotaops_team_member_role,
  true
from demo_profile_seed p
where p.team_key = 'rune-raiders'
on conflict (id) do update
set registration_id = excluded.registration_id,
    profile_id = excluded.profile_id,
    team_member_id = excluded.team_member_id,
    member_role = excluded.member_role,
    is_starter = true,
    manual_player_id = null,
    manual_display_name = null,
    manual_nickname = null,
    manual_note = null,
    updated_at = now();

insert into public.tournament_groups (
  id,
  tournament_id,
  name,
  sort_order,
  settings
)
values
  (
    pg_temp.demo_uuid('group:demo-cup:a'),
    pg_temp.demo_uuid('tournament:demo-cup'),
    'Demo Group A',
    1,
    '{"demo":true}'::jsonb
  )
on conflict (tournament_id, name) do update
set sort_order = excluded.sort_order,
    settings = excluded.settings,
    updated_at = now();

insert into public.tournament_group_teams (
  id,
  group_id,
  team_id,
  registration_id,
  seed_number
)
select
  pg_temp.demo_uuid('group-team:demo-cup:a:' || team_key),
  pg_temp.demo_uuid('group:demo-cup:a'),
  pg_temp.demo_uuid('team:' || team_key),
  pg_temp.demo_uuid('registration:demo-cup:' || team_key),
  seed_number
from demo_team_seed
where registration_status = 'approved'
on conflict (group_id, team_id) do update
set registration_id = excluded.registration_id,
    seed_number = excluded.seed_number,
    updated_at = now();

create temporary table demo_match_seed (
  match_key text primary key,
  stage_name text not null,
  group_key text,
  round_name text not null,
  round_number integer not null,
  bracket_position integer,
  status text not null,
  scheduled_at timestamptz,
  started_at timestamptz,
  finished_at timestamptz,
  best_of integer not null,
  team_a_key text,
  team_b_key text,
  score_a integer not null,
  score_b integer not null,
  winner_team_key text
) on commit drop;

insert into demo_match_seed (
  match_key,
  stage_name,
  group_key,
  round_name,
  round_number,
  bracket_position,
  status,
  scheduled_at,
  started_at,
  finished_at,
  best_of,
  team_a_key,
  team_b_key,
  score_a,
  score_b,
  winner_team_key
)
values
  ('group-wolves-ravens', 'Group Stage', 'a', 'Group Round 1', 1, 1, 'finished', timestamptz '2026-06-01 14:00:00+00', timestamptz '2026-06-01 14:05:00+00', timestamptz '2026-06-01 17:05:00+00', 3, 'radiant-wolves', 'dire-ravens', 2, 1, 'radiant-wolves'),
  ('group-titans-hunters', 'Group Stage', 'a', 'Group Round 1', 1, 2, 'finished', timestamptz '2026-06-01 18:00:00+00', timestamptz '2026-06-01 18:04:00+00', timestamptz '2026-06-01 20:20:00+00', 3, 'ancient-titans', 'roshan-hunters', 2, 0, 'ancient-titans'),
  ('group-wolves-titans', 'Group Stage', 'a', 'Group Round 2', 2, 1, 'finished', timestamptz '2026-06-02 14:00:00+00', timestamptz '2026-06-02 14:03:00+00', timestamptz '2026-06-02 16:10:00+00', 3, 'radiant-wolves', 'ancient-titans', 2, 0, 'radiant-wolves'),
  ('group-ravens-hunters', 'Group Stage', 'a', 'Group Round 2', 2, 2, 'scheduled', timestamptz '2026-06-03 18:00:00+00', null, null, 3, 'dire-ravens', 'roshan-hunters', 0, 0, null),
  ('semi-1', 'Playoffs', null, 'Semifinal', 1, 1, 'finished', timestamptz '2026-06-04 16:00:00+00', timestamptz '2026-06-04 16:03:00+00', timestamptz '2026-06-04 18:20:00+00', 3, 'radiant-wolves', 'roshan-hunters', 2, 0, 'radiant-wolves'),
  ('semi-2', 'Playoffs', null, 'Semifinal', 1, 2, 'finished', timestamptz '2026-06-04 19:00:00+00', timestamptz '2026-06-04 19:03:00+00', timestamptz '2026-06-04 22:10:00+00', 3, 'ancient-titans', 'dire-ravens', 2, 1, 'ancient-titans'),
  ('final', 'Playoffs', null, 'Final', 2, 1, 'scheduled', timestamptz '2026-06-06 18:00:00+00', null, null, 5, 'radiant-wolves', 'ancient-titans', 0, 0, null);

insert into public.matches (
  id,
  tournament_id,
  group_id,
  stage_name,
  round_name,
  round_number,
  bracket_position,
  status,
  scheduled_at,
  started_at,
  finished_at,
  best_of,
  team_a_id,
  team_b_id,
  score_a,
  score_b,
  winner_team_id,
  settings,
  notes
)
select
  pg_temp.demo_uuid('match:' || match_key),
  pg_temp.demo_uuid('tournament:demo-cup'),
  case when group_key is null then null else pg_temp.demo_uuid('group:demo-cup:' || group_key) end,
  stage_name,
  round_name,
  round_number,
  bracket_position,
  status::public.dotaops_match_status,
  scheduled_at,
  started_at,
  finished_at,
  best_of,
  case when team_a_key is null then null else pg_temp.demo_uuid('team:' || team_a_key) end,
  case when team_b_key is null then null else pg_temp.demo_uuid('team:' || team_b_key) end,
  score_a,
  score_b,
  case when winner_team_key is null then null else pg_temp.demo_uuid('team:' || winner_team_key) end,
  jsonb_build_object('demo', true),
  'Synthetic demo match seeded by BE/DB-27.'
from demo_match_seed
on conflict (id) do update
set group_id = excluded.group_id,
    stage_name = excluded.stage_name,
    round_name = excluded.round_name,
    round_number = excluded.round_number,
    bracket_position = excluded.bracket_position,
    status = excluded.status,
    scheduled_at = excluded.scheduled_at,
    started_at = excluded.started_at,
    finished_at = excluded.finished_at,
    best_of = excluded.best_of,
    team_a_id = excluded.team_a_id,
    team_b_id = excluded.team_b_id,
    score_a = excluded.score_a,
    score_b = excluded.score_b,
    winner_team_id = excluded.winner_team_id,
    settings = excluded.settings,
    notes = excluded.notes,
    updated_at = now();

insert into public.match_slots (
  id,
  match_id,
  slot,
  source_type,
  team_id,
  source_match_id,
  source_registration_id,
  seed_number,
  display_label
)
select
  pg_temp.demo_uuid('match-slot:' || match_key || ':team-a'),
  pg_temp.demo_uuid('match:' || match_key),
  'team_a'::public.dotaops_match_slot,
  case when match_key = 'final' then 'winner' else 'seed' end::public.dotaops_match_slot_source,
  case when team_a_key is null then null else pg_temp.demo_uuid('team:' || team_a_key) end,
  case when match_key = 'final' then pg_temp.demo_uuid('match:semi-1') else null end,
  case when team_a_key is null then null else pg_temp.demo_uuid('registration:demo-cup:' || team_a_key) end,
  case when team_a_key is null then null else (select seed_number from demo_team_seed dts where dts.team_key = team_a_key) end,
  case when match_key = 'final' then 'Winner of semifinal 1' else null end
from demo_match_seed
where stage_name = 'Playoffs'
on conflict (match_id, slot) do update
set source_type = excluded.source_type,
    team_id = excluded.team_id,
    source_match_id = excluded.source_match_id,
    source_registration_id = excluded.source_registration_id,
    seed_number = excluded.seed_number,
    display_label = excluded.display_label,
    updated_at = now();

insert into public.match_slots (
  id,
  match_id,
  slot,
  source_type,
  team_id,
  source_match_id,
  source_registration_id,
  seed_number,
  display_label
)
select
  pg_temp.demo_uuid('match-slot:' || match_key || ':team-b'),
  pg_temp.demo_uuid('match:' || match_key),
  'team_b'::public.dotaops_match_slot,
  case when match_key = 'final' then 'winner' else 'seed' end::public.dotaops_match_slot_source,
  case when team_b_key is null then null else pg_temp.demo_uuid('team:' || team_b_key) end,
  case when match_key = 'final' then pg_temp.demo_uuid('match:semi-2') else null end,
  case when team_b_key is null then null else pg_temp.demo_uuid('registration:demo-cup:' || team_b_key) end,
  case when team_b_key is null then null else (select seed_number from demo_team_seed dts where dts.team_key = team_b_key) end,
  case when match_key = 'final' then 'Winner of semifinal 2' else null end
from demo_match_seed
where stage_name = 'Playoffs'
on conflict (match_id, slot) do update
set source_type = excluded.source_type,
    team_id = excluded.team_id,
    source_match_id = excluded.source_match_id,
    source_registration_id = excluded.source_registration_id,
    seed_number = excluded.seed_number,
    display_label = excluded.display_label,
    updated_at = now();

create temporary table demo_game_seed (
  game_key text primary key,
  match_key text not null,
  game_number integer not null,
  radiant_team_key text not null,
  dire_team_key text not null,
  winner_team_key text not null,
  dota_match_id text not null,
  duration_seconds integer not null,
  started_at timestamptz not null
) on commit drop;

insert into demo_game_seed (
  game_key,
  match_key,
  game_number,
  radiant_team_key,
  dire_team_key,
  winner_team_key,
  dota_match_id,
  duration_seconds,
  started_at
)
values
  ('gwr-1', 'group-wolves-ravens', 1, 'radiant-wolves', 'dire-ravens', 'radiant-wolves', '9100000001', 2250, timestamptz '2026-06-01 14:05:00+00'),
  ('gwr-2', 'group-wolves-ravens', 2, 'dire-ravens', 'radiant-wolves', 'dire-ravens', '9100000002', 2410, timestamptz '2026-06-01 15:05:00+00'),
  ('gwr-3', 'group-wolves-ravens', 3, 'radiant-wolves', 'dire-ravens', 'radiant-wolves', '9100000003', 2110, timestamptz '2026-06-01 16:15:00+00'),
  ('gth-1', 'group-titans-hunters', 1, 'ancient-titans', 'roshan-hunters', 'ancient-titans', '9100000004', 1980, timestamptz '2026-06-01 18:04:00+00'),
  ('gth-2', 'group-titans-hunters', 2, 'roshan-hunters', 'ancient-titans', 'ancient-titans', '9100000005', 2160, timestamptz '2026-06-01 19:08:00+00'),
  ('gwt-1', 'group-wolves-titans', 1, 'radiant-wolves', 'ancient-titans', 'radiant-wolves', '9100000006', 2050, timestamptz '2026-06-02 14:03:00+00'),
  ('gwt-2', 'group-wolves-titans', 2, 'ancient-titans', 'radiant-wolves', 'radiant-wolves', '9100000007', 2250, timestamptz '2026-06-02 15:05:00+00'),
  ('s1-1', 'semi-1', 1, 'radiant-wolves', 'roshan-hunters', 'radiant-wolves', '9100000008', 2320, timestamptz '2026-06-04 16:03:00+00'),
  ('s1-2', 'semi-1', 2, 'roshan-hunters', 'radiant-wolves', 'radiant-wolves', '9100000009', 2460, timestamptz '2026-06-04 17:07:00+00'),
  ('s2-1', 'semi-2', 1, 'ancient-titans', 'dire-ravens', 'dire-ravens', '9100000010', 2550, timestamptz '2026-06-04 19:03:00+00'),
  ('s2-2', 'semi-2', 2, 'dire-ravens', 'ancient-titans', 'ancient-titans', '9100000011', 2370, timestamptz '2026-06-04 20:07:00+00'),
  ('s2-3', 'semi-2', 3, 'ancient-titans', 'dire-ravens', 'ancient-titans', '9100000012', 2680, timestamptz '2026-06-04 21:04:00+00');

insert into public.match_games (
  id,
  match_id,
  game_number,
  status,
  import_status,
  dota_match_id,
  radiant_team_id,
  dire_team_id,
  winner_team_id,
  duration_seconds,
  started_at,
  finished_at,
  raw_summary,
  raw_response,
  normalized_payload,
  radiant_win,
  game_mode,
  lobby_type,
  radiant_score,
  dire_score,
  winner_side
)
select
  pg_temp.demo_uuid('match-game:' || game_key),
  pg_temp.demo_uuid('match:' || match_key),
  game_number,
  'finished'::public.dotaops_match_status,
  'ready'::public.dotaops_import_status,
  dota_match_id,
  pg_temp.demo_uuid('team:' || radiant_team_key),
  pg_temp.demo_uuid('team:' || dire_team_key),
  pg_temp.demo_uuid('team:' || winner_team_key),
  duration_seconds,
  started_at,
  started_at + (duration_seconds || ' seconds')::interval,
  jsonb_build_object('demo', true, 'dotaMatchId', dota_match_id),
  jsonb_build_object('demo', true, 'source', 'BE/DB-27'),
  jsonb_build_object('demo', true, 'playersNormalized', 10, 'durationSeconds', duration_seconds),
  winner_team_key = radiant_team_key,
  22,
  1,
  case when winner_team_key = radiant_team_key then 42 else 28 end,
  case when winner_team_key = dire_team_key then 42 else 28 end,
  case when winner_team_key = radiant_team_key then 'RADIANT' else 'DIRE' end
from demo_game_seed
on conflict (id) do update
set match_id = excluded.match_id,
    game_number = excluded.game_number,
    status = excluded.status,
    import_status = excluded.import_status,
    dota_match_id = excluded.dota_match_id,
    radiant_team_id = excluded.radiant_team_id,
    dire_team_id = excluded.dire_team_id,
    winner_team_id = excluded.winner_team_id,
    duration_seconds = excluded.duration_seconds,
    started_at = excluded.started_at,
    finished_at = excluded.finished_at,
    raw_summary = excluded.raw_summary,
    raw_response = excluded.raw_response,
    normalized_payload = excluded.normalized_payload,
    radiant_win = excluded.radiant_win,
    game_mode = excluded.game_mode,
    lobby_type = excluded.lobby_type,
    radiant_score = excluded.radiant_score,
    dire_score = excluded.dire_score,
    winner_side = excluded.winner_side,
    updated_at = now();

insert into public.match_imports (
  id,
  match_id,
  match_game_id,
  dota_match_id,
  status,
  requested_by,
  raw_response,
  normalized_payload,
  source,
  attempt_count,
  requested_at,
  started_at,
  completed_at,
  metadata
)
select
  pg_temp.demo_uuid('match-import:' || game_key),
  pg_temp.demo_uuid('match:' || match_key),
  pg_temp.demo_uuid('match-game:' || game_key),
  dota_match_id,
  'ready'::public.dotaops_import_status,
  pg_temp.demo_uuid('profile:organizer'),
  jsonb_build_object('demo', true, 'dotaMatchId', dota_match_id),
  jsonb_build_object('demo', true, 'normalizedAt', (started_at + interval '5 minutes')),
  'demo-seed',
  1,
  started_at + interval '2 minutes',
  started_at + interval '3 minutes',
  started_at + interval '5 minutes',
  jsonb_build_object('demo', true, 'source', 'BE/DB-27')
from demo_game_seed
on conflict (id) do update
set match_id = excluded.match_id,
    match_game_id = excluded.match_game_id,
    dota_match_id = excluded.dota_match_id,
    status = excluded.status,
    requested_by = excluded.requested_by,
    raw_response = excluded.raw_response,
    normalized_payload = excluded.normalized_payload,
    source = excluded.source,
    attempt_count = excluded.attempt_count,
    requested_at = excluded.requested_at,
    started_at = excluded.started_at,
    completed_at = excluded.completed_at,
    metadata = excluded.metadata,
    error_message = null,
    updated_at = now();

insert into public.match_import_events (
  id,
  match_import_id,
  status,
  message,
  payload,
  created_by,
  created_at
)
select
  pg_temp.demo_uuid('match-import-event:' || game_key || ':ready'),
  pg_temp.demo_uuid('match-import:' || game_key),
  'ready'::public.dotaops_import_status,
  'Demo match import normalized successfully.',
  jsonb_build_object('demo', true, 'dotaMatchId', dota_match_id),
  pg_temp.demo_uuid('profile:organizer'),
  started_at + interval '5 minutes'
from demo_game_seed
on conflict (id) do update
set match_import_id = excluded.match_import_id,
    status = excluded.status,
    message = excluded.message,
    payload = excluded.payload,
    created_by = excluded.created_by,
    created_at = excluded.created_at;

insert into public.match_players (
  id,
  match_import_id,
  match_id,
  match_game_id,
  team_id,
  profile_id,
  hero_id,
  dota_hero_id,
  steam_account_id,
  dota_account_id,
  player_slot,
  is_radiant,
  team_side,
  is_winner,
  kills,
  deaths,
  assists,
  last_hits,
  denies,
  gold_per_min,
  xp_per_min,
  net_worth,
  hero_damage,
  tower_damage,
  hero_healing,
  level,
  duration_seconds,
  lane_role,
  item_ids,
  items,
  benchmarks,
  raw_player
)
select
  pg_temp.demo_uuid('match-player:' || g.game_key || ':' || p.profile_key),
  pg_temp.demo_uuid('match-import:' || g.game_key),
  pg_temp.demo_uuid('match:' || g.match_key),
  pg_temp.demo_uuid('match-game:' || g.game_key),
  pg_temp.demo_uuid('team:' || p.team_key),
  pg_temp.demo_uuid('profile:' || p.profile_key),
  h.id,
  h.dota_hero_id,
  p.dota_account_id::text,
  p.dota_account_id,
  case when p.team_key = g.radiant_team_key then p.slot_order - 1 else 127 + p.slot_order end,
  p.team_key = g.radiant_team_key,
  case when p.team_key = g.radiant_team_key then 'RADIANT' else 'DIRE' end,
  p.team_key = g.winner_team_key,
  case
    when p.profile_key = 'player-01' and p.team_key = g.winner_team_key then 18 + g.game_number
    when p.team_key = g.winner_team_key and p.slot_order = 1 then 11 + g.game_number
    when p.team_key = g.winner_team_key then 6 + p.slot_order + g.game_number
    when p.profile_key = 'player-07' then 2 + g.game_number
    else 2 + p.slot_order
  end,
  case
    when p.profile_key = 'player-01' and p.team_key = g.winner_team_key then 1
    when p.profile_key = 'player-07' then 12
    when p.team_key = g.winner_team_key then 1 + (p.slot_order % 2)
    else 5 + p.slot_order
  end,
  case
    when p.profile_key = 'player-01' and p.team_key = g.winner_team_key then 14 + g.game_number
    when p.team_key = g.winner_team_key then 10 + p.slot_order + g.game_number
    else 5 + p.slot_order
  end,
  case when p.slot_order in (1, 2) then 180 + (g.game_number * 8) + (p.slot_order * 15) else 45 + (p.slot_order * 8) end,
  case when p.slot_order in (1, 2) then 12 + p.slot_order else 3 + p.slot_order end,
  case when p.team_key = g.winner_team_key then 520 + (p.slot_order * 22) else 380 + (p.slot_order * 18) end,
  case when p.team_key = g.winner_team_key then 610 + (p.slot_order * 20) else 430 + (p.slot_order * 17) end,
  case when p.team_key = g.winner_team_key then 18000 + (p.slot_order * 900) else 11500 + (p.slot_order * 700) end,
  case when p.team_key = g.winner_team_key then 26000 + (p.slot_order * 1200) else 14500 + (p.slot_order * 900) end,
  case when p.slot_order = 1 and p.team_key = g.winner_team_key then 4200 else 800 + (p.slot_order * 300) end,
  case when p.slot_order >= 4 then 2200 + (p.slot_order * 250) else 250 end,
  case when p.team_key = g.winner_team_key then 24 else 18 end,
  g.duration_seconds,
  p.member_role,
  array[50 + p.slot_order, 100 + p.slot_order, 150 + p.slot_order],
  jsonb_build_object('item_0', 50 + p.slot_order, 'item_1', 100 + p.slot_order, 'item_2', 150 + p.slot_order),
  jsonb_build_object('demo', true, 'laneEfficiency', case when p.team_key = g.winner_team_key then 'high' else 'contested' end),
  jsonb_build_object('demo', true, 'profileKey', p.profile_key, 'gameKey', g.game_key)
from demo_game_seed g
join demo_profile_seed p
  on p.team_key in (g.radiant_team_key, g.dire_team_key)
join public.heroes h
  on h.dota_hero_id = case
    when p.slot_order = 1 then 74
    when p.slot_order = 2 and g.game_number % 2 = 0 then 11
    when p.slot_order = 2 then 14
    when p.slot_order = 3 then 2
    when p.slot_order = 4 and g.game_number % 2 = 0 then 25
    when p.slot_order = 4 then 5
    when p.slot_order = 5 then 86
    else 8
  end
on conflict (id) do update
set match_import_id = excluded.match_import_id,
    match_id = excluded.match_id,
    match_game_id = excluded.match_game_id,
    team_id = excluded.team_id,
    profile_id = excluded.profile_id,
    hero_id = excluded.hero_id,
    dota_hero_id = excluded.dota_hero_id,
    steam_account_id = excluded.steam_account_id,
    dota_account_id = excluded.dota_account_id,
    player_slot = excluded.player_slot,
    is_radiant = excluded.is_radiant,
    team_side = excluded.team_side,
    is_winner = excluded.is_winner,
    kills = excluded.kills,
    deaths = excluded.deaths,
    assists = excluded.assists,
    last_hits = excluded.last_hits,
    denies = excluded.denies,
    gold_per_min = excluded.gold_per_min,
    xp_per_min = excluded.xp_per_min,
    net_worth = excluded.net_worth,
    hero_damage = excluded.hero_damage,
    tower_damage = excluded.tower_damage,
    hero_healing = excluded.hero_healing,
    level = excluded.level,
    duration_seconds = excluded.duration_seconds,
    lane_role = excluded.lane_role,
    item_ids = excluded.item_ids,
    items = excluded.items,
    benchmarks = excluded.benchmarks,
    raw_player = excluded.raw_player,
    updated_at = now();

insert into public.team_invitations (
  id,
  team_id,
  inviter_profile_id,
  invitee_profile_id,
  invitee_email,
  proposed_role,
  status,
  token_hash,
  expires_at
)
values
  (
    pg_temp.demo_uuid('team-invitation:radiant-wolves:player-30'),
    pg_temp.demo_uuid('team:radiant-wolves'),
    pg_temp.demo_uuid('profile:player-01'),
    pg_temp.demo_uuid('profile:player-30'),
    'demo.player30@dotaops.local',
    'support'::public.dotaops_team_member_role,
    'pending'::public.dotaops_invitation_status,
    null,
    timestamptz '2026-06-20 12:00:00+00'
  )
on conflict (id) do update
set team_id = excluded.team_id,
    inviter_profile_id = excluded.inviter_profile_id,
    invitee_profile_id = excluded.invitee_profile_id,
    invitee_email = excluded.invitee_email,
    proposed_role = excluded.proposed_role,
    status = excluded.status,
    token_hash = null,
    expires_at = excluded.expires_at,
    accepted_at = null,
    updated_at = now();

insert into public.team_join_requests (
  id,
  team_id,
  requester_profile_id,
  message,
  status,
  resolved_at,
  resolved_by_profile_id
)
values
  (
    pg_temp.demo_uuid('team-join-request:midlane-mages:radiant-wolves'),
    pg_temp.demo_uuid('team:midlane-mages'),
    pg_temp.demo_uuid('profile:player-05'),
    'Demo pending join request for captain workflow coverage.',
    'pending'::public.dotaops_team_join_request_status,
    null,
    null
  )
on conflict (id) do update
set team_id = excluded.team_id,
    requester_profile_id = excluded.requester_profile_id,
    message = excluded.message,
    status = excluded.status,
    resolved_at = null,
    resolved_by_profile_id = null,
    updated_at = now();

select private.refresh_dotaops_analytics();

commit;
