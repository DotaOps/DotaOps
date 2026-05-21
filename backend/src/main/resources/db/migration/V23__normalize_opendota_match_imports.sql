alter table public.match_games
  alter column match_id drop not null;

alter table public.match_games
  add column if not exists raw_response jsonb,
  add column if not exists normalized_payload jsonb,
  add column if not exists radiant_win boolean,
  add column if not exists game_mode integer,
  add column if not exists lobby_type integer,
  add column if not exists radiant_score integer,
  add column if not exists dire_score integer,
  add column if not exists winner_side text;

alter table public.match_games drop constraint if exists match_games_winner_side_allowed;
alter table public.match_games add constraint match_games_winner_side_allowed
  check (winner_side is null or winner_side in ('RADIANT', 'DIRE'));

alter table public.match_players
  add column if not exists team_side text,
  add column if not exists dota_account_id bigint,
  add column if not exists items jsonb not null default '{}'::jsonb;

alter table public.match_players drop constraint if exists match_players_team_side_allowed;
alter table public.match_players add constraint match_players_team_side_allowed
  check (team_side is null or team_side in ('RADIANT', 'DIRE'));

alter table public.match_players drop constraint if exists match_players_dota_account_id_range;
alter table public.match_players add constraint match_players_dota_account_id_range
  check (
    dota_account_id is null
    or (dota_account_id between 0 and 4294967295)
  );

update public.match_players
set team_side = case
    when is_radiant is true then 'RADIANT'
    when is_radiant is false then 'DIRE'
    else null
  end
where team_side is null;

create index if not exists match_games_dota_match_id_idx
  on public.match_games(dota_match_id)
  where dota_match_id is not null;

create index if not exists match_players_dota_account_id_idx
  on public.match_players(dota_account_id)
  where dota_account_id is not null;

create index if not exists match_players_team_side_idx
  on public.match_players(team_side)
  where team_side is not null;

create index if not exists match_players_match_game_id_idx
  on public.match_players(match_game_id);

create or replace function private.sync_match_import_match_game()
returns trigger
language plpgsql
set search_path = public
as $$
declare
  linked_match_id uuid;
  linked_dota_match_id text;
begin
  if new.match_game_id is null and new.dota_match_id is not null then
    select mg.id
    into new.match_game_id
    from public.match_games mg
    where mg.dota_match_id = new.dota_match_id
    limit 1;
  end if;

  if new.match_game_id is not null then
    select mg.match_id, mg.dota_match_id
    into linked_match_id, linked_dota_match_id
    from public.match_games mg
    where mg.id = new.match_game_id;

    if not found then
      raise exception 'match_game_id does not reference an existing match game.';
    end if;

    if linked_match_id is not null then
      if new.match_id is not null and new.match_id <> linked_match_id then
        raise exception 'match_imports.match_id must match match_games.match_id.';
      end if;

      new.match_id = linked_match_id;
    end if;

    if linked_dota_match_id is not null and linked_dota_match_id <> new.dota_match_id then
      raise exception 'match_imports.dota_match_id must match match_games.dota_match_id.';
    end if;
  end if;

  return new;
end;
$$;

grant select (
  radiant_win,
  game_mode,
  lobby_type,
  radiant_score,
  dire_score,
  winner_side
) on public.match_games to anon, authenticated;

grant select (
  dota_hero_id,
  team_side,
  items
) on public.match_players to anon, authenticated;

comment on column public.match_games.raw_response is
  'Original OpenDota match payload stored for server/admin debugging only.';
comment on column public.match_games.normalized_payload is
  'Backend normalization summary used to audit import shape without querying raw OpenDota JSON.';
comment on column public.match_players.items is
  'Normalized OpenDota item slots including item_0..item_5, backpack_0..backpack_2 and item_neutral.';
