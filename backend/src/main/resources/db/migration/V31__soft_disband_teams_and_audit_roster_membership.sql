alter table public.teams
  add column if not exists disbanded_at timestamptz;

comment on column public.teams.disbanded_at is
  'Soft-delete marker. Disbanded teams keep tournament history but are no longer active or publicly discoverable.';

create index if not exists teams_active_created_at_idx
  on public.teams(created_at desc, id desc)
  where disbanded_at is null;

create or replace function private.is_team_captain(target_team_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.teams t
    where t.id = target_team_id
      and t.disbanded_at is null
      and t.captain_profile_id = private.current_profile_id()
  )
$$;

drop policy if exists "teams are publicly readable" on public.teams;
drop policy if exists "active teams are publicly readable" on public.teams;
create policy "active teams are publicly readable"
on public.teams for select
to anon, authenticated
using (disbanded_at is null);

drop policy if exists "team members are publicly readable" on public.team_members;
drop policy if exists "active team members are publicly readable" on public.team_members;
create policy "active team members are publicly readable"
on public.team_members for select
to anon, authenticated
using (
  exists (
    select 1
    from public.teams t
    where t.id = team_id
      and t.disbanded_at is null
  )
);

drop policy if exists "manual players are publicly readable" on public.team_manual_players;
drop policy if exists "active team manual players are publicly readable" on public.team_manual_players;
create policy "active team manual players are publicly readable"
on public.team_manual_players for select
to anon, authenticated
using (
  exists (
    select 1
    from public.teams t
    where t.id = team_id
      and t.disbanded_at is null
  )
);

drop policy if exists "players create own team join requests" on public.team_join_requests;
create policy "players create own team join requests"
on public.team_join_requests for insert
to authenticated
with check (
  requester_profile_id = (select private.current_profile_id())
  and exists (
    select 1
    from public.profiles p
    where p.id = (select private.current_profile_id())
      and p.role = 'player'::public.dotaops_user_role
  )
  and exists (
    select 1
    from public.teams t
    where t.id = team_id
      and t.disbanded_at is null
  )
);

drop trigger if exists audit_team_members on public.team_members;
create trigger audit_team_members
after insert or update or delete on public.team_members
for each row execute function private.write_audit_log();
