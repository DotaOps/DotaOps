alter table public.heroes
  add column if not exists slug text,
  add column if not exists image_url text,
  add column if not exists icon_url text;

create unique index if not exists heroes_dota_hero_id_idx
  on public.heroes(dota_hero_id);

alter table public.match_players
  add column if not exists dota_hero_id integer;

update public.match_players mp
set dota_hero_id = h.dota_hero_id
from public.heroes h
where mp.hero_id = h.id
  and mp.dota_hero_id is null;

create index if not exists match_players_dota_hero_id_idx
  on public.match_players(dota_hero_id)
  where dota_hero_id is not null;

create index if not exists match_players_hero_id_idx
  on public.match_players(hero_id);
