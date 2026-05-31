alter table public.teams
  add column if not exists banner_url text;

create table if not exists public.team_manual_players (
  id uuid primary key default gen_random_uuid(),
  team_id uuid not null references public.teams(id) on delete cascade,
  display_name text not null,
  nickname text,
  note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint team_manual_players_display_name_length check (char_length(display_name) between 1 and 80),
  constraint team_manual_players_nickname_length check (nickname is null or char_length(nickname) between 1 and 80),
  constraint team_manual_players_note_length check (note is null or char_length(note) <= 500)
);

create index if not exists team_manual_players_team_id_idx
  on public.team_manual_players(team_id);

drop trigger if exists team_manual_players_set_updated_at on public.team_manual_players;
create trigger team_manual_players_set_updated_at
before update on public.team_manual_players
for each row execute function public.set_updated_at();

alter table public.tournament_registration_members
  alter column profile_id drop not null,
  alter column member_role drop not null,
  add column if not exists manual_player_id uuid references public.team_manual_players(id) on delete set null,
  add column if not exists manual_display_name text,
  add column if not exists manual_nickname text,
  add column if not exists manual_note text;

alter table public.tournament_registration_members
  drop constraint if exists tournament_registration_members_participant_present;
alter table public.tournament_registration_members
  add constraint tournament_registration_members_participant_present
  check (
    (profile_id is not null and manual_player_id is null)
    or (profile_id is null and manual_player_id is not null)
  );

create unique index if not exists tournament_registration_members_registration_manual_player_idx
  on public.tournament_registration_members(registration_id, manual_player_id)
  where manual_player_id is not null;

alter table public.tournaments
  drop constraint if exists tournaments_settings_team_size_supported;
alter table public.tournaments
  add constraint tournaments_settings_team_size_supported
  check (coalesce(nullif(settings->>'teamSize', '')::integer, 5) in (1, 3, 5));

create or replace function private.validate_registration_starter_limit()
returns trigger
language plpgsql
set search_path = public
as $$
declare
  roster_limit integer;
begin
  select coalesce(nullif(t.settings->>'teamSize', '')::integer, 5)
  into roster_limit
  from public.tournament_registrations tr
  join public.tournaments t on t.id = tr.tournament_id
  where tr.id = new.registration_id;

  roster_limit := coalesce(roster_limit, 5);

  if new.is_starter and (
    select count(*)
    from public.tournament_registration_members trm
    where trm.registration_id = new.registration_id
      and trm.is_starter
      and trm.id <> coalesce(new.id, '00000000-0000-0000-0000-000000000000'::uuid)
  ) >= roster_limit then
    raise exception 'A tournament registration can have at most % starters.', roster_limit;
  end if;

  return new;
end;
$$;

do $$
begin
  create type public.dotaops_team_join_request_status as enum (
    'pending',
    'accepted',
    'declined',
    'cancelled'
  );
exception
  when duplicate_object then null;
end;
$$;

create table if not exists public.team_join_requests (
  id uuid primary key default gen_random_uuid(),
  team_id uuid not null references public.teams(id) on delete cascade,
  requester_profile_id uuid not null references public.profiles(id) on delete cascade,
  message text,
  status public.dotaops_team_join_request_status not null default 'pending',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  resolved_at timestamptz,
  resolved_by_profile_id uuid references public.profiles(id) on delete set null,
  constraint team_join_requests_message_length check (message is null or char_length(message) <= 1000),
  constraint team_join_requests_resolution_required check (
    (status = 'pending' and resolved_at is null)
    or (status <> 'pending' and resolved_at is not null)
  )
);

create unique index if not exists team_join_requests_pending_requester_idx
  on public.team_join_requests(team_id, requester_profile_id)
  where status = 'pending';

create index if not exists team_join_requests_team_id_idx
  on public.team_join_requests(team_id);

create index if not exists team_join_requests_requester_profile_id_idx
  on public.team_join_requests(requester_profile_id);

create index if not exists team_join_requests_status_idx
  on public.team_join_requests(status);

drop trigger if exists team_join_requests_set_updated_at on public.team_join_requests;
create trigger team_join_requests_set_updated_at
before update on public.team_join_requests
for each row execute function public.set_updated_at();

alter table public.team_manual_players enable row level security;
alter table public.team_join_requests enable row level security;

drop policy if exists "manual players are publicly readable" on public.team_manual_players;
create policy "manual players are publicly readable"
on public.team_manual_players for select
to anon, authenticated
using (true);

drop policy if exists "captains manage manual players" on public.team_manual_players;
create policy "captains manage manual players"
on public.team_manual_players for all
to authenticated
using (
  private.is_team_captain(team_id)
  or (select private.is_organizer_or_admin())
)
with check (
  private.is_team_captain(team_id)
  or (select private.is_organizer_or_admin())
);

drop policy if exists "team join requests are visible to requester and captains" on public.team_join_requests;
create policy "team join requests are visible to requester and captains"
on public.team_join_requests for select
to authenticated
using (
  requester_profile_id = (select private.current_profile_id())
  or private.is_team_captain(team_id)
  or (select private.is_organizer_or_admin())
);

drop policy if exists "players create own team join requests" on public.team_join_requests;
create policy "players create own team join requests"
on public.team_join_requests for insert
to authenticated
with check (requester_profile_id = (select private.current_profile_id()));

drop policy if exists "requesters and captains update team join requests" on public.team_join_requests;
create policy "requesters and captains update team join requests"
on public.team_join_requests for update
to authenticated
using (
  requester_profile_id = (select private.current_profile_id())
  or private.is_team_captain(team_id)
)
with check (
  (
    requester_profile_id = (select private.current_profile_id())
    and status = 'cancelled'
    and resolved_by_profile_id = (select private.current_profile_id())
  )
  or private.is_team_captain(team_id)
);

grant select on public.team_manual_players to anon, authenticated;
grant insert, update, delete on public.team_manual_players to authenticated;
grant select, insert, update on public.team_join_requests to authenticated;

grant all on
  public.team_manual_players,
  public.team_join_requests
to service_role;
