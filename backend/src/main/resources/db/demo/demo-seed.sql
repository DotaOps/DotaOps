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
  (pg_temp.demo_uuid('hero:1'), 1, 'npc_dota_hero_antimage', 'Anti-Mage', 'agi', 'Melee', array['Carry', 'Escape', 'Nuker'], 'antimage', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/antimage.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/antimage.png'),
  (pg_temp.demo_uuid('hero:2'), 2, 'npc_dota_hero_axe', 'Axe', 'str', 'Melee', array['Initiator', 'Durable', 'Disabler', 'Carry'], 'axe', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/axe.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/axe.png'),
  (pg_temp.demo_uuid('hero:3'), 3, 'npc_dota_hero_bane', 'Bane', 'all', 'Ranged', array['Support', 'Disabler', 'Nuker', 'Durable'], 'bane', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/bane.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/bane.png'),
  (pg_temp.demo_uuid('hero:4'), 4, 'npc_dota_hero_bloodseeker', 'Bloodseeker', 'agi', 'Melee', array['Carry', 'Disabler', 'Nuker', 'Initiator'], 'bloodseeker', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/bloodseeker.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/bloodseeker.png'),
  (pg_temp.demo_uuid('hero:5'), 5, 'npc_dota_hero_crystal_maiden', 'Crystal Maiden', 'int', 'Ranged', array['Support', 'Disabler', 'Nuker'], 'crystal-maiden', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/crystal_maiden.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/crystal_maiden.png'),
  (pg_temp.demo_uuid('hero:6'), 6, 'npc_dota_hero_drow_ranger', 'Drow Ranger', 'agi', 'Ranged', array['Carry', 'Disabler', 'Pusher'], 'drow-ranger', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/drow_ranger.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/drow_ranger.png'),
  (pg_temp.demo_uuid('hero:7'), 7, 'npc_dota_hero_earthshaker', 'Earthshaker', 'str', 'Melee', array['Support', 'Initiator', 'Disabler', 'Nuker'], 'earthshaker', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/earthshaker.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/earthshaker.png'),
  (pg_temp.demo_uuid('hero:8'), 8, 'npc_dota_hero_juggernaut', 'Juggernaut', 'agi', 'Melee', array['Carry', 'Pusher', 'Escape'], 'juggernaut', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/juggernaut.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/juggernaut.png'),
  (pg_temp.demo_uuid('hero:9'), 9, 'npc_dota_hero_mirana', 'Mirana', 'agi', 'Ranged', array['Carry', 'Support', 'Escape', 'Nuker', 'Disabler'], 'mirana', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/mirana.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/mirana.png'),
  (pg_temp.demo_uuid('hero:10'), 10, 'npc_dota_hero_morphling', 'Morphling', 'agi', 'Ranged', array['Carry', 'Escape', 'Durable', 'Nuker', 'Disabler'], 'morphling', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/morphling.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/morphling.png'),
  (pg_temp.demo_uuid('hero:11'), 11, 'npc_dota_hero_nevermore', 'Shadow Fiend', 'agi', 'Ranged', array['Carry', 'Nuker'], 'nevermore', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/nevermore.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/nevermore.png'),
  (pg_temp.demo_uuid('hero:12'), 12, 'npc_dota_hero_phantom_lancer', 'Phantom Lancer', 'agi', 'Melee', array['Carry', 'Escape', 'Pusher', 'Nuker'], 'phantom-lancer', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/phantom_lancer.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/phantom_lancer.png'),
  (pg_temp.demo_uuid('hero:13'), 13, 'npc_dota_hero_puck', 'Puck', 'int', 'Ranged', array['Initiator', 'Disabler', 'Escape', 'Nuker'], 'puck', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/puck.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/puck.png'),
  (pg_temp.demo_uuid('hero:14'), 14, 'npc_dota_hero_pudge', 'Pudge', 'str', 'Melee', array['Disabler', 'Initiator', 'Durable', 'Nuker'], 'pudge', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/pudge.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/pudge.png'),
  (pg_temp.demo_uuid('hero:15'), 15, 'npc_dota_hero_razor', 'Razor', 'agi', 'Ranged', array['Carry', 'Durable', 'Nuker', 'Pusher'], 'razor', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/razor.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/razor.png'),
  (pg_temp.demo_uuid('hero:16'), 16, 'npc_dota_hero_sand_king', 'Sand King', 'all', 'Melee', array['Initiator', 'Disabler', 'Support', 'Nuker', 'Escape'], 'sand-king', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/sand_king.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/sand_king.png'),
  (pg_temp.demo_uuid('hero:17'), 17, 'npc_dota_hero_storm_spirit', 'Storm Spirit', 'int', 'Ranged', array['Carry', 'Escape', 'Nuker', 'Initiator', 'Disabler'], 'storm-spirit', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/storm_spirit.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/storm_spirit.png'),
  (pg_temp.demo_uuid('hero:18'), 18, 'npc_dota_hero_sven', 'Sven', 'str', 'Melee', array['Carry', 'Disabler', 'Initiator', 'Durable', 'Nuker'], 'sven', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/sven.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/sven.png'),
  (pg_temp.demo_uuid('hero:19'), 19, 'npc_dota_hero_tiny', 'Tiny', 'str', 'Melee', array['Carry', 'Nuker', 'Pusher', 'Initiator', 'Durable', 'Disabler'], 'tiny', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/tiny.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/tiny.png'),
  (pg_temp.demo_uuid('hero:20'), 20, 'npc_dota_hero_vengefulspirit', 'Vengeful Spirit', 'agi', 'Ranged', array['Support', 'Initiator', 'Disabler', 'Nuker', 'Escape'], 'vengefulspirit', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/vengefulspirit.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/vengefulspirit.png'),
  (pg_temp.demo_uuid('hero:21'), 21, 'npc_dota_hero_windrunner', 'Windranger', 'all', 'Ranged', array['Carry', 'Support', 'Disabler', 'Escape', 'Nuker'], 'windrunner', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/windrunner.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/windrunner.png'),
  (pg_temp.demo_uuid('hero:22'), 22, 'npc_dota_hero_zuus', 'Zeus', 'int', 'Ranged', array['Nuker', 'Carry'], 'zuus', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/zuus.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/zuus.png'),
  (pg_temp.demo_uuid('hero:23'), 23, 'npc_dota_hero_kunkka', 'Kunkka', 'str', 'Melee', array['Carry', 'Support', 'Disabler', 'Initiator', 'Durable', 'Nuker'], 'kunkka', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/kunkka.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/kunkka.png'),
  (pg_temp.demo_uuid('hero:25'), 25, 'npc_dota_hero_lina', 'Lina', 'int', 'Ranged', array['Support', 'Carry', 'Nuker', 'Disabler'], 'lina', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/lina.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/lina.png'),
  (pg_temp.demo_uuid('hero:26'), 26, 'npc_dota_hero_lion', 'Lion', 'int', 'Ranged', array['Support', 'Disabler', 'Nuker', 'Initiator'], 'lion', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/lion.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/lion.png'),
  (pg_temp.demo_uuid('hero:27'), 27, 'npc_dota_hero_shadow_shaman', 'Shadow Shaman', 'int', 'Ranged', array['Support', 'Pusher', 'Disabler', 'Nuker', 'Initiator'], 'shadow-shaman', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/shadow_shaman.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/shadow_shaman.png'),
  (pg_temp.demo_uuid('hero:28'), 28, 'npc_dota_hero_slardar', 'Slardar', 'str', 'Melee', array['Carry', 'Durable', 'Initiator', 'Disabler', 'Escape'], 'slardar', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/slardar.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/slardar.png'),
  (pg_temp.demo_uuid('hero:29'), 29, 'npc_dota_hero_tidehunter', 'Tidehunter', 'str', 'Melee', array['Initiator', 'Durable', 'Disabler', 'Nuker', 'Carry'], 'tidehunter', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/tidehunter.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/tidehunter.png'),
  (pg_temp.demo_uuid('hero:30'), 30, 'npc_dota_hero_witch_doctor', 'Witch Doctor', 'int', 'Ranged', array['Support', 'Nuker', 'Disabler'], 'witch-doctor', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/witch_doctor.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/witch_doctor.png'),
  (pg_temp.demo_uuid('hero:31'), 31, 'npc_dota_hero_lich', 'Lich', 'int', 'Ranged', array['Support', 'Nuker'], 'lich', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/lich.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/lich.png'),
  (pg_temp.demo_uuid('hero:32'), 32, 'npc_dota_hero_riki', 'Riki', 'agi', 'Melee', array['Carry', 'Escape', 'Disabler'], 'riki', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/riki.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/riki.png'),
  (pg_temp.demo_uuid('hero:33'), 33, 'npc_dota_hero_enigma', 'Enigma', 'all', 'Ranged', array['Disabler', 'Initiator', 'Pusher'], 'enigma', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/enigma.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/enigma.png'),
  (pg_temp.demo_uuid('hero:34'), 34, 'npc_dota_hero_tinker', 'Tinker', 'int', 'Ranged', array['Carry', 'Nuker', 'Pusher'], 'tinker', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/tinker.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/tinker.png'),
  (pg_temp.demo_uuid('hero:35'), 35, 'npc_dota_hero_sniper', 'Sniper', 'agi', 'Ranged', array['Carry', 'Nuker'], 'sniper', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/sniper.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/sniper.png'),
  (pg_temp.demo_uuid('hero:36'), 36, 'npc_dota_hero_necrolyte', 'Necrophos', 'int', 'Ranged', array['Carry', 'Nuker', 'Durable', 'Disabler'], 'necrolyte', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/necrolyte.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/necrolyte.png'),
  (pg_temp.demo_uuid('hero:37'), 37, 'npc_dota_hero_warlock', 'Warlock', 'int', 'Ranged', array['Support', 'Initiator', 'Disabler'], 'warlock', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/warlock.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/warlock.png'),
  (pg_temp.demo_uuid('hero:38'), 38, 'npc_dota_hero_beastmaster', 'Beastmaster', 'all', 'Melee', array['Initiator', 'Disabler', 'Durable', 'Nuker'], 'beastmaster', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/beastmaster.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/beastmaster.png'),
  (pg_temp.demo_uuid('hero:39'), 39, 'npc_dota_hero_queenofpain', 'Queen of Pain', 'int', 'Ranged', array['Carry', 'Nuker', 'Escape'], 'queenofpain', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/queenofpain.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/queenofpain.png'),
  (pg_temp.demo_uuid('hero:40'), 40, 'npc_dota_hero_venomancer', 'Venomancer', 'all', 'Ranged', array['Support', 'Nuker', 'Initiator', 'Pusher', 'Disabler'], 'venomancer', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/venomancer.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/venomancer.png'),
  (pg_temp.demo_uuid('hero:41'), 41, 'npc_dota_hero_faceless_void', 'Faceless Void', 'agi', 'Melee', array['Carry', 'Initiator', 'Disabler', 'Escape', 'Durable'], 'faceless-void', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/faceless_void.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/faceless_void.png'),
  (pg_temp.demo_uuid('hero:42'), 42, 'npc_dota_hero_skeleton_king', 'Wraith King', 'str', 'Melee', array['Carry', 'Support', 'Durable', 'Disabler', 'Initiator'], 'skeleton-king', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/skeleton_king.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/skeleton_king.png'),
  (pg_temp.demo_uuid('hero:43'), 43, 'npc_dota_hero_death_prophet', 'Death Prophet', 'all', 'Ranged', array['Carry', 'Pusher', 'Nuker', 'Disabler'], 'death-prophet', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/death_prophet.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/death_prophet.png'),
  (pg_temp.demo_uuid('hero:44'), 44, 'npc_dota_hero_phantom_assassin', 'Phantom Assassin', 'agi', 'Melee', array['Carry', 'Escape'], 'phantom-assassin', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/phantom_assassin.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/phantom_assassin.png'),
  (pg_temp.demo_uuid('hero:45'), 45, 'npc_dota_hero_pugna', 'Pugna', 'int', 'Ranged', array['Nuker', 'Pusher'], 'pugna', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/pugna.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/pugna.png'),
  (pg_temp.demo_uuid('hero:46'), 46, 'npc_dota_hero_templar_assassin', 'Templar Assassin', 'agi', 'Ranged', array['Carry', 'Escape'], 'templar-assassin', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/templar_assassin.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/templar_assassin.png'),
  (pg_temp.demo_uuid('hero:47'), 47, 'npc_dota_hero_viper', 'Viper', 'agi', 'Ranged', array['Carry', 'Durable', 'Initiator', 'Disabler'], 'viper', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/viper.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/viper.png'),
  (pg_temp.demo_uuid('hero:48'), 48, 'npc_dota_hero_luna', 'Luna', 'agi', 'Ranged', array['Carry', 'Nuker', 'Pusher'], 'luna', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/luna.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/luna.png'),
  (pg_temp.demo_uuid('hero:49'), 49, 'npc_dota_hero_dragon_knight', 'Dragon Knight', 'str', 'Melee', array['Carry', 'Pusher', 'Durable', 'Disabler', 'Initiator', 'Nuker'], 'dragon-knight', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/dragon_knight.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/dragon_knight.png'),
  (pg_temp.demo_uuid('hero:50'), 50, 'npc_dota_hero_dazzle', 'Dazzle', 'all', 'Ranged', array['Support', 'Nuker', 'Disabler'], 'dazzle', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/dazzle.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/dazzle.png'),
  (pg_temp.demo_uuid('hero:51'), 51, 'npc_dota_hero_rattletrap', 'Clockwerk', 'str', 'Melee', array['Initiator', 'Disabler', 'Durable', 'Nuker'], 'rattletrap', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/rattletrap.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/rattletrap.png'),
  (pg_temp.demo_uuid('hero:52'), 52, 'npc_dota_hero_leshrac', 'Leshrac', 'int', 'Ranged', array['Carry', 'Support', 'Nuker', 'Pusher', 'Disabler'], 'leshrac', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/leshrac.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/leshrac.png'),
  (pg_temp.demo_uuid('hero:53'), 53, 'npc_dota_hero_furion', 'Nature''s Prophet', 'all', 'Ranged', array['Carry', 'Pusher', 'Escape', 'Nuker'], 'furion', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/furion.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/furion.png'),
  (pg_temp.demo_uuid('hero:54'), 54, 'npc_dota_hero_life_stealer', 'Lifestealer', 'str', 'Melee', array['Carry', 'Durable', 'Escape', 'Disabler'], 'life-stealer', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/life_stealer.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/life_stealer.png'),
  (pg_temp.demo_uuid('hero:55'), 55, 'npc_dota_hero_dark_seer', 'Dark Seer', 'int', 'Melee', array['Initiator', 'Escape', 'Disabler'], 'dark-seer', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/dark_seer.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/dark_seer.png'),
  (pg_temp.demo_uuid('hero:56'), 56, 'npc_dota_hero_clinkz', 'Clinkz', 'agi', 'Ranged', array['Carry', 'Escape', 'Pusher'], 'clinkz', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/clinkz.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/clinkz.png'),
  (pg_temp.demo_uuid('hero:57'), 57, 'npc_dota_hero_omniknight', 'Omniknight', 'str', 'Melee', array['Support', 'Durable', 'Nuker'], 'omniknight', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/omniknight.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/omniknight.png'),
  (pg_temp.demo_uuid('hero:58'), 58, 'npc_dota_hero_enchantress', 'Enchantress', 'int', 'Ranged', array['Support', 'Pusher', 'Durable', 'Disabler'], 'enchantress', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/enchantress.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/enchantress.png'),
  (pg_temp.demo_uuid('hero:59'), 59, 'npc_dota_hero_huskar', 'Huskar', 'str', 'Ranged', array['Carry', 'Durable', 'Initiator'], 'huskar', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/huskar.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/huskar.png'),
  (pg_temp.demo_uuid('hero:60'), 60, 'npc_dota_hero_night_stalker', 'Night Stalker', 'str', 'Melee', array['Carry', 'Initiator', 'Durable', 'Disabler', 'Nuker'], 'night-stalker', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/night_stalker.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/night_stalker.png'),
  (pg_temp.demo_uuid('hero:61'), 61, 'npc_dota_hero_broodmother', 'Broodmother', 'agi', 'Melee', array['Carry', 'Pusher', 'Escape', 'Nuker'], 'broodmother', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/broodmother.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/broodmother.png'),
  (pg_temp.demo_uuid('hero:62'), 62, 'npc_dota_hero_bounty_hunter', 'Bounty Hunter', 'agi', 'Melee', array['Escape', 'Nuker'], 'bounty-hunter', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/bounty_hunter.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/bounty_hunter.png'),
  (pg_temp.demo_uuid('hero:63'), 63, 'npc_dota_hero_weaver', 'Weaver', 'agi', 'Ranged', array['Carry', 'Escape'], 'weaver', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/weaver.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/weaver.png'),
  (pg_temp.demo_uuid('hero:64'), 64, 'npc_dota_hero_jakiro', 'Jakiro', 'int', 'Ranged', array['Support', 'Nuker', 'Pusher', 'Disabler'], 'jakiro', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/jakiro.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/jakiro.png'),
  (pg_temp.demo_uuid('hero:65'), 65, 'npc_dota_hero_batrider', 'Batrider', 'all', 'Ranged', array['Initiator', 'Disabler', 'Escape'], 'batrider', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/batrider.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/batrider.png'),
  (pg_temp.demo_uuid('hero:66'), 66, 'npc_dota_hero_chen', 'Chen', 'int', 'Ranged', array['Support', 'Pusher'], 'chen', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/chen.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/chen.png'),
  (pg_temp.demo_uuid('hero:67'), 67, 'npc_dota_hero_spectre', 'Spectre', 'agi', 'Melee', array['Carry', 'Durable', 'Escape'], 'spectre', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/spectre.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/spectre.png'),
  (pg_temp.demo_uuid('hero:68'), 68, 'npc_dota_hero_ancient_apparition', 'Ancient Apparition', 'int', 'Ranged', array['Support', 'Disabler', 'Nuker'], 'ancient-apparition', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/ancient_apparition.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/ancient_apparition.png'),
  (pg_temp.demo_uuid('hero:69'), 69, 'npc_dota_hero_doom_bringer', 'Doom', 'str', 'Melee', array['Carry', 'Disabler', 'Initiator', 'Durable', 'Nuker'], 'doom-bringer', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/doom_bringer.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/doom_bringer.png'),
  (pg_temp.demo_uuid('hero:70'), 70, 'npc_dota_hero_ursa', 'Ursa', 'agi', 'Melee', array['Carry', 'Durable', 'Disabler'], 'ursa', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/ursa.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/ursa.png'),
  (pg_temp.demo_uuid('hero:71'), 71, 'npc_dota_hero_spirit_breaker', 'Spirit Breaker', 'str', 'Melee', array['Carry', 'Initiator', 'Disabler', 'Durable', 'Escape'], 'spirit-breaker', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/spirit_breaker.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/spirit_breaker.png'),
  (pg_temp.demo_uuid('hero:72'), 72, 'npc_dota_hero_gyrocopter', 'Gyrocopter', 'agi', 'Ranged', array['Carry', 'Nuker', 'Disabler'], 'gyrocopter', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/gyrocopter.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/gyrocopter.png'),
  (pg_temp.demo_uuid('hero:73'), 73, 'npc_dota_hero_alchemist', 'Alchemist', 'str', 'Melee', array['Carry', 'Support', 'Durable', 'Disabler', 'Initiator', 'Nuker'], 'alchemist', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/alchemist.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/alchemist.png'),
  (pg_temp.demo_uuid('hero:74'), 74, 'npc_dota_hero_invoker', 'Invoker', 'int', 'Ranged', array['Carry', 'Nuker', 'Disabler', 'Escape', 'Pusher'], 'invoker', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/invoker.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/invoker.png'),
  (pg_temp.demo_uuid('hero:75'), 75, 'npc_dota_hero_silencer', 'Silencer', 'int', 'Ranged', array['Carry', 'Support', 'Disabler', 'Initiator', 'Nuker'], 'silencer', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/silencer.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/silencer.png'),
  (pg_temp.demo_uuid('hero:76'), 76, 'npc_dota_hero_obsidian_destroyer', 'Outworld Destroyer', 'int', 'Ranged', array['Carry', 'Nuker', 'Disabler'], 'obsidian-destroyer', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/obsidian_destroyer.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/obsidian_destroyer.png'),
  (pg_temp.demo_uuid('hero:77'), 77, 'npc_dota_hero_lycan', 'Lycan', 'str', 'Melee', array['Carry', 'Pusher', 'Durable', 'Escape'], 'lycan', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/lycan.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/lycan.png'),
  (pg_temp.demo_uuid('hero:78'), 78, 'npc_dota_hero_brewmaster', 'Brewmaster', 'all', 'Melee', array['Carry', 'Initiator', 'Durable', 'Disabler', 'Nuker'], 'brewmaster', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/brewmaster.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/brewmaster.png'),
  (pg_temp.demo_uuid('hero:79'), 79, 'npc_dota_hero_shadow_demon', 'Shadow Demon', 'int', 'Ranged', array['Support', 'Disabler', 'Initiator', 'Nuker'], 'shadow-demon', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/shadow_demon.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/shadow_demon.png'),
  (pg_temp.demo_uuid('hero:80'), 80, 'npc_dota_hero_lone_druid', 'Lone Druid', 'agi', 'Ranged', array['Carry', 'Pusher', 'Durable'], 'lone-druid', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/lone_druid.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/lone_druid.png'),
  (pg_temp.demo_uuid('hero:81'), 81, 'npc_dota_hero_chaos_knight', 'Chaos Knight', 'str', 'Melee', array['Carry', 'Disabler', 'Durable', 'Pusher', 'Initiator'], 'chaos-knight', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/chaos_knight.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/chaos_knight.png'),
  (pg_temp.demo_uuid('hero:82'), 82, 'npc_dota_hero_meepo', 'Meepo', 'agi', 'Melee', array['Carry', 'Escape', 'Nuker', 'Disabler', 'Initiator', 'Pusher'], 'meepo', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/meepo.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/meepo.png'),
  (pg_temp.demo_uuid('hero:83'), 83, 'npc_dota_hero_treant', 'Treant Protector', 'str', 'Melee', array['Support', 'Initiator', 'Durable', 'Disabler', 'Escape'], 'treant', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/treant.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/treant.png'),
  (pg_temp.demo_uuid('hero:84'), 84, 'npc_dota_hero_ogre_magi', 'Ogre Magi', 'str', 'Melee', array['Support', 'Nuker', 'Disabler', 'Durable', 'Initiator'], 'ogre-magi', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/ogre_magi.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/ogre_magi.png'),
  (pg_temp.demo_uuid('hero:85'), 85, 'npc_dota_hero_undying', 'Undying', 'str', 'Melee', array['Support', 'Durable', 'Disabler', 'Nuker'], 'undying', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/undying.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/undying.png'),
  (pg_temp.demo_uuid('hero:86'), 86, 'npc_dota_hero_rubick', 'Rubick', 'int', 'Ranged', array['Support', 'Disabler', 'Nuker'], 'rubick', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/rubick.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/rubick.png'),
  (pg_temp.demo_uuid('hero:87'), 87, 'npc_dota_hero_disruptor', 'Disruptor', 'int', 'Ranged', array['Support', 'Disabler', 'Nuker', 'Initiator'], 'disruptor', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/disruptor.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/disruptor.png'),
  (pg_temp.demo_uuid('hero:88'), 88, 'npc_dota_hero_nyx_assassin', 'Nyx Assassin', 'all', 'Melee', array['Disabler', 'Nuker', 'Initiator', 'Escape'], 'nyx-assassin', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/nyx_assassin.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/nyx_assassin.png'),
  (pg_temp.demo_uuid('hero:89'), 89, 'npc_dota_hero_naga_siren', 'Naga Siren', 'agi', 'Melee', array['Carry', 'Support', 'Pusher', 'Disabler', 'Initiator', 'Escape'], 'naga-siren', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/naga_siren.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/naga_siren.png'),
  (pg_temp.demo_uuid('hero:90'), 90, 'npc_dota_hero_keeper_of_the_light', 'Keeper of the Light', 'int', 'Ranged', array['Support', 'Nuker', 'Disabler'], 'keeper-of-the-light', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/keeper_of_the_light.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/keeper_of_the_light.png'),
  (pg_temp.demo_uuid('hero:91'), 91, 'npc_dota_hero_wisp', 'Io', 'all', 'Ranged', array['Support', 'Escape', 'Nuker'], 'wisp', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/wisp.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/wisp.png'),
  (pg_temp.demo_uuid('hero:92'), 92, 'npc_dota_hero_visage', 'Visage', 'all', 'Ranged', array['Support', 'Nuker', 'Durable', 'Disabler', 'Pusher'], 'visage', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/visage.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/visage.png'),
  (pg_temp.demo_uuid('hero:93'), 93, 'npc_dota_hero_slark', 'Slark', 'agi', 'Melee', array['Carry', 'Escape', 'Disabler', 'Nuker'], 'slark', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/slark.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/slark.png'),
  (pg_temp.demo_uuid('hero:94'), 94, 'npc_dota_hero_medusa', 'Medusa', 'agi', 'Ranged', array['Carry', 'Disabler', 'Durable'], 'medusa', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/medusa.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/medusa.png'),
  (pg_temp.demo_uuid('hero:95'), 95, 'npc_dota_hero_troll_warlord', 'Troll Warlord', 'agi', 'Ranged', array['Carry', 'Pusher', 'Disabler', 'Durable'], 'troll-warlord', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/troll_warlord.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/troll_warlord.png'),
  (pg_temp.demo_uuid('hero:96'), 96, 'npc_dota_hero_centaur', 'Centaur Warrunner', 'str', 'Melee', array['Durable', 'Initiator', 'Disabler', 'Nuker', 'Escape'], 'centaur', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/centaur.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/centaur.png'),
  (pg_temp.demo_uuid('hero:97'), 97, 'npc_dota_hero_magnataur', 'Magnus', 'all', 'Melee', array['Initiator', 'Disabler', 'Nuker', 'Escape'], 'magnataur', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/magnataur.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/magnataur.png'),
  (pg_temp.demo_uuid('hero:98'), 98, 'npc_dota_hero_shredder', 'Timbersaw', 'str', 'Melee', array['Nuker', 'Durable', 'Escape'], 'shredder', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/shredder.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/shredder.png'),
  (pg_temp.demo_uuid('hero:99'), 99, 'npc_dota_hero_bristleback', 'Bristleback', 'str', 'Melee', array['Carry', 'Durable', 'Initiator', 'Nuker'], 'bristleback', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/bristleback.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/bristleback.png'),
  (pg_temp.demo_uuid('hero:100'), 100, 'npc_dota_hero_tusk', 'Tusk', 'str', 'Melee', array['Initiator', 'Disabler', 'Nuker'], 'tusk', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/tusk.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/tusk.png'),
  (pg_temp.demo_uuid('hero:101'), 101, 'npc_dota_hero_skywrath_mage', 'Skywrath Mage', 'int', 'Ranged', array['Support', 'Nuker', 'Disabler'], 'skywrath-mage', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/skywrath_mage.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/skywrath_mage.png'),
  (pg_temp.demo_uuid('hero:102'), 102, 'npc_dota_hero_abaddon', 'Abaddon', 'all', 'Melee', array['Support', 'Carry', 'Durable'], 'abaddon', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/abaddon.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/abaddon.png'),
  (pg_temp.demo_uuid('hero:103'), 103, 'npc_dota_hero_elder_titan', 'Elder Titan', 'str', 'Melee', array['Initiator', 'Disabler', 'Nuker', 'Durable'], 'elder-titan', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/elder_titan.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/elder_titan.png'),
  (pg_temp.demo_uuid('hero:104'), 104, 'npc_dota_hero_legion_commander', 'Legion Commander', 'str', 'Melee', array['Carry', 'Disabler', 'Initiator', 'Durable', 'Nuker'], 'legion-commander', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/legion_commander.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/legion_commander.png'),
  (pg_temp.demo_uuid('hero:105'), 105, 'npc_dota_hero_techies', 'Techies', 'all', 'Ranged', array['Nuker', 'Disabler'], 'techies', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/techies.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/techies.png'),
  (pg_temp.demo_uuid('hero:106'), 106, 'npc_dota_hero_ember_spirit', 'Ember Spirit', 'agi', 'Melee', array['Carry', 'Escape', 'Nuker', 'Disabler', 'Initiator'], 'ember-spirit', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/ember_spirit.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/ember_spirit.png'),
  (pg_temp.demo_uuid('hero:107'), 107, 'npc_dota_hero_earth_spirit', 'Earth Spirit', 'str', 'Melee', array['Nuker', 'Escape', 'Disabler', 'Initiator', 'Durable'], 'earth-spirit', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/earth_spirit.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/earth_spirit.png'),
  (pg_temp.demo_uuid('hero:108'), 108, 'npc_dota_hero_abyssal_underlord', 'Underlord', 'str', 'Melee', array['Support', 'Nuker', 'Disabler', 'Durable', 'Escape'], 'abyssal-underlord', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/abyssal_underlord.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/abyssal_underlord.png'),
  (pg_temp.demo_uuid('hero:109'), 109, 'npc_dota_hero_terrorblade', 'Terrorblade', 'agi', 'Melee', array['Carry', 'Pusher', 'Nuker'], 'terrorblade', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/terrorblade.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/terrorblade.png'),
  (pg_temp.demo_uuid('hero:110'), 110, 'npc_dota_hero_phoenix', 'Phoenix', 'str', 'Ranged', array['Support', 'Nuker', 'Initiator', 'Escape', 'Disabler'], 'phoenix', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/phoenix.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/phoenix.png'),
  (pg_temp.demo_uuid('hero:111'), 111, 'npc_dota_hero_oracle', 'Oracle', 'int', 'Ranged', array['Support', 'Nuker', 'Disabler', 'Escape'], 'oracle', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/oracle.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/oracle.png'),
  (pg_temp.demo_uuid('hero:112'), 112, 'npc_dota_hero_winter_wyvern', 'Winter Wyvern', 'int', 'Ranged', array['Support', 'Disabler', 'Nuker'], 'winter-wyvern', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/winter_wyvern.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/winter_wyvern.png'),
  (pg_temp.demo_uuid('hero:113'), 113, 'npc_dota_hero_arc_warden', 'Arc Warden', 'all', 'Ranged', array['Carry', 'Escape', 'Nuker'], 'arc-warden', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/arc_warden.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/arc_warden.png'),
  (pg_temp.demo_uuid('hero:114'), 114, 'npc_dota_hero_monkey_king', 'Monkey King', 'agi', 'Melee', array['Carry', 'Escape', 'Disabler', 'Initiator'], 'monkey-king', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/monkey_king.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/monkey_king.png'),
  (pg_temp.demo_uuid('hero:119'), 119, 'npc_dota_hero_dark_willow', 'Dark Willow', 'int', 'Ranged', array['Support', 'Nuker', 'Disabler', 'Escape'], 'dark-willow', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/dark_willow.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/dark_willow.png'),
  (pg_temp.demo_uuid('hero:120'), 120, 'npc_dota_hero_pangolier', 'Pangolier', 'all', 'Melee', array['Carry', 'Nuker', 'Disabler', 'Durable', 'Escape', 'Initiator'], 'pangolier', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/pangolier.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/pangolier.png'),
  (pg_temp.demo_uuid('hero:121'), 121, 'npc_dota_hero_grimstroke', 'Grimstroke', 'int', 'Ranged', array['Support', 'Nuker', 'Disabler', 'Escape'], 'grimstroke', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/grimstroke.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/grimstroke.png'),
  (pg_temp.demo_uuid('hero:123'), 123, 'npc_dota_hero_hoodwink', 'Hoodwink', 'agi', 'Ranged', array['Support', 'Nuker', 'Escape', 'Disabler'], 'hoodwink', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/hoodwink.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/hoodwink.png'),
  (pg_temp.demo_uuid('hero:126'), 126, 'npc_dota_hero_void_spirit', 'Void Spirit', 'all', 'Melee', array['Carry', 'Escape', 'Nuker', 'Disabler'], 'void-spirit', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/void_spirit.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/void_spirit.png'),
  (pg_temp.demo_uuid('hero:128'), 128, 'npc_dota_hero_snapfire', 'Snapfire', 'all', 'Ranged', array['Support', 'Nuker', 'Disabler', 'Escape'], 'snapfire', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/snapfire.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/snapfire.png'),
  (pg_temp.demo_uuid('hero:129'), 129, 'npc_dota_hero_mars', 'Mars', 'str', 'Melee', array['Carry', 'Initiator', 'Disabler', 'Durable'], 'mars', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/mars.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/mars.png'),
  (pg_temp.demo_uuid('hero:131'), 131, 'npc_dota_hero_ringmaster', 'Ringmaster', 'int', 'Ranged', array['Support', 'Nuker', 'Escape', 'Disabler'], 'ringmaster', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/ringmaster.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/ringmaster.png'),
  (pg_temp.demo_uuid('hero:135'), 135, 'npc_dota_hero_dawnbreaker', 'Dawnbreaker', 'str', 'Melee', array['Carry', 'Durable'], 'dawnbreaker', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/dawnbreaker.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/dawnbreaker.png'),
  (pg_temp.demo_uuid('hero:136'), 136, 'npc_dota_hero_marci', 'Marci', 'all', 'Melee', array['Support', 'Carry', 'Initiator', 'Disabler', 'Escape'], 'marci', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/marci.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/marci.png'),
  (pg_temp.demo_uuid('hero:137'), 137, 'npc_dota_hero_primal_beast', 'Primal Beast', 'str', 'Melee', array['Initiator', 'Durable', 'Disabler'], 'primal-beast', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/primal_beast.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/primal_beast.png'),
  (pg_temp.demo_uuid('hero:138'), 138, 'npc_dota_hero_muerta', 'Muerta', 'int', 'Ranged', array['Carry', 'Nuker', 'Disabler'], 'muerta', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/muerta.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/muerta.png'),
  (pg_temp.demo_uuid('hero:145'), 145, 'npc_dota_hero_kez', 'Kez', 'agi', 'Melee', array['Carry', 'Escape', 'Disabler'], 'kez', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/kez.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/kez.png'),
  (pg_temp.demo_uuid('hero:155'), 155, 'npc_dota_hero_largo', 'Largo', 'str', 'Melee', array['Durable', 'Disabler', 'Support'], 'largo', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/largo.png', 'https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/largo.png')
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
