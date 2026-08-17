-- Auto-provisioning is intentionally fail-closed. Signup metadata can describe
-- onboarding intent, but only the Spring application flow may persist an
-- organizer capability after authentication.
create or replace function private.self_selected_profile_role(
  p_user_metadata jsonb,
  p_app_metadata jsonb
)
returns public.dotaops_user_role
language sql
immutable
security definer
set search_path = ''
as $$
  select 'player'::public.dotaops_user_role
$$;

comment on function private.self_selected_profile_role(jsonb, jsonb) is
  'Returns PLAYER for automatic auth-user provisioning. Browser-controlled metadata never grants application roles.';

create or replace function private.is_organizer()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.profiles p
    where p.auth_user_id = (select auth.uid())
      and p.role = 'organizer'::public.dotaops_user_role
  )
$$;

revoke all on function private.is_organizer() from public, anon, authenticated, service_role;
grant execute on function private.is_organizer() to authenticated, service_role;

create or replace function private.is_active_team_member(target_team_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.teams t
    join public.team_members tm
      on tm.team_id = t.id
     and tm.is_active = true
     and tm.left_at is null
    join public.profiles p
      on p.id = tm.profile_id
    where t.id = target_team_id
      and t.disbanded_at is null
      and p.auth_user_id = (select auth.uid())
      and p.role = 'player'::public.dotaops_user_role
  )
$$;

create or replace function private.is_team_captain(target_team_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.teams t
    join public.profiles p
      on p.id = t.captain_profile_id
    join public.team_members tm
      on tm.team_id = t.id
     and tm.profile_id = p.id
     and tm.is_active = true
     and tm.left_at is null
    where t.id = target_team_id
      and t.disbanded_at is null
      and p.auth_user_id = (select auth.uid())
      and p.role = 'player'::public.dotaops_user_role
  )
$$;

comment on function private.is_team_captain(uuid) is
  'True only for a PLAYER who is the captain and an active roster member of the concrete, non-disbanded team.';

create or replace function private.can_manage_tournament(target_tournament_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select (select private.is_admin())
    or (
      (select private.is_organizer())
      and (
        exists (
          select 1
          from public.tournaments t
          where t.id = target_tournament_id
            and t.organizer_profile_id = (select private.current_profile_id())
        )
        or exists (
          select 1
          from public.tournament_staff ts
          where ts.tournament_id = target_tournament_id
            and ts.profile_id = (select private.current_profile_id())
            and ts.staff_role in (
              'owner'::public.dotaops_tournament_staff_role,
              'organizer'::public.dotaops_tournament_staff_role
            )
        )
      )
    )
$$;

comment on function private.can_manage_tournament(uuid) is
  'ADMIN has an explicit global override. ORGANIZER additionally needs owner or owner/organizer staff scope for the concrete tournament.';

-- Profile writes remain Spring/admin workflows. Even a temporary broad table
-- grant must not let an authenticated user change their persisted application
-- role or any other profile fields directly.
drop policy if exists "users update own profile details" on public.profiles;
create policy "admins update profiles"
on public.profiles for update
to authenticated
using ((select private.is_admin()))
with check ((select private.is_admin()));

drop policy if exists "external accounts are owner readable" on public.profile_external_accounts;
create policy "external accounts are owner readable"
on public.profile_external_accounts for select
to authenticated
using (
  profile_id = (select private.current_profile_id())
  or (select private.is_admin())
);

drop policy if exists "users read own notifications" on public.notification_outbox;
create policy "users read own notifications"
on public.notification_outbox for select
to authenticated
using (
  recipient_profile_id = (select private.current_profile_id())
  or (select private.is_admin())
);

-- Tournament creation is a global organizer action, but the created object must
-- be owned by that organizer. Existing-object management is always object scoped.
drop policy if exists "organizers create tournaments" on public.tournaments;
create policy "organizers create own tournaments"
on public.tournaments for insert
to authenticated
with check (
  (select private.is_admin())
  or (
    (select private.is_organizer())
    and organizer_profile_id = (select private.current_profile_id())
    and created_by = (select auth.uid())
  )
);

-- The UPDATE policy's capability helper evaluates the stored tournament row.
-- Preserve ordinary owner/delegated-organizer edits, but fail closed when a
-- non-admin Data API actor tries to transfer ownership or rewrite provenance.
create or replace function private.enforce_tournament_ownership_transition()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if auth.uid() is not null
     and not private.is_admin()
     and (
       new.organizer_profile_id is distinct from old.organizer_profile_id
       or new.created_by is distinct from old.created_by
     ) then
    raise exception using
      errcode = '42501',
      message = 'Tournament ownership and provenance can only be changed by an admin workflow.';
  end if;

  return new;
end;
$$;

revoke all on function private.enforce_tournament_ownership_transition()
  from public, anon, authenticated, service_role;

drop trigger if exists enforce_tournament_ownership_transition on public.tournaments;
create trigger enforce_tournament_ownership_transition
before update of organizer_profile_id, created_by on public.tournaments
for each row
execute function private.enforce_tournament_ownership_transition();

-- Captains may edit ordinary fields of their active team, but direct ownership
-- transfer and disband state changes remain backend workflows.
drop policy if exists "captains and admins update teams" on public.teams;
create policy "captains and admins update teams"
on public.teams for update
to authenticated
using (
  private.is_team_captain(id)
  or (select private.is_admin())
)
with check (
  (select private.is_admin())
  or (
    private.is_team_captain(id)
    and captain_profile_id = (select private.current_profile_id())
    and disbanded_at is null
  )
);

-- Membership identity and lifecycle changes are Spring business workflows.
-- Keep an explicit admin RLS override, while ordinary captain workflows use the
-- backend database role and cannot become generic Data API row mutations.
drop policy if exists "captains and admins insert team members" on public.team_members;
drop policy if exists "captains and admins update team members" on public.team_members;
drop policy if exists "captains and admins delete team members" on public.team_members;

create policy "admins insert team members"
on public.team_members for insert
to authenticated
with check ((select private.is_admin()));

create policy "admins update team members"
on public.team_members for update
to authenticated
using ((select private.is_admin()))
with check ((select private.is_admin()));

create policy "admins delete team members"
on public.team_members for delete
to authenticated
using ((select private.is_admin()));

-- A team registration can only be initiated by that team's active captain and
-- only in the unreviewed pending state. Tournament managers review it later.
drop policy if exists "captains create registrations" on public.tournament_registrations;
create policy "captains create pending registrations"
on public.tournament_registrations for insert
to authenticated
with check (
  (select private.is_admin())
  or (
    private.is_team_captain(team_id)
    and captain_profile_id = (select private.current_profile_id())
    and status = 'pending'::public.dotaops_registration_status
    and reviewed_by is null
    and reviewed_at is null
    and seed_number is null
    and checked_in_at is null
  )
);

-- Registration roster snapshots are produced by the backend registration
-- workflow; they are not a second direct captain membership surface.
drop policy if exists "captains insert registration members" on public.tournament_registration_members;
drop policy if exists "captains update registration members" on public.tournament_registration_members;
drop policy if exists "captains delete registration members" on public.tournament_registration_members;

create policy "admins insert registration members"
on public.tournament_registration_members for insert
to authenticated
with check ((select private.is_admin()));

create policy "admins update registration members"
on public.tournament_registration_members for update
to authenticated
using ((select private.is_admin()))
with check ((select private.is_admin()));

create policy "admins delete registration members"
on public.tournament_registration_members for delete
to authenticated
using ((select private.is_admin()));

-- Hero catalogue maintenance is an explicit admin capability.
drop policy if exists "organizers insert heroes" on public.heroes;
drop policy if exists "organizers update heroes" on public.heroes;
drop policy if exists "organizers delete heroes" on public.heroes;

create policy "admins insert heroes"
on public.heroes for insert
to authenticated
with check ((select private.is_admin()));

create policy "admins update heroes"
on public.heroes for update
to authenticated
using ((select private.is_admin()))
with check ((select private.is_admin()));

create policy "admins delete heroes"
on public.heroes for delete
to authenticated
using ((select private.is_admin()));

-- Organizer import access must come from the linked match/tournament. Detailed
-- requester, retry and event ownership remains intentionally deferred to #146.
drop policy if exists "organizers request imports" on public.match_imports;
create policy "tournament managers and admins request imports"
on public.match_imports for insert
to authenticated
with check (
  (select private.is_admin())
  or (
    match_id is not null
    and private.can_officiate_match(match_id)
  )
  or (
    match_game_id is not null
    and private.can_officiate_match_game(match_game_id)
  )
);

drop policy if exists "officials update imports" on public.match_imports;
create policy "tournament managers and admins update imports"
on public.match_imports for update
to authenticated
using (
  (select private.is_admin())
  or (match_id is not null and private.can_officiate_match(match_id))
  or (match_game_id is not null and private.can_officiate_match_game(match_game_id))
)
with check (
  (select private.is_admin())
  or (match_id is not null and private.can_officiate_match(match_id))
  or (match_game_id is not null and private.can_officiate_match_game(match_game_id))
);

drop policy if exists "officials insert match advancement audit logs" on public.match_advancement_audit_logs;
create policy "tournament managers insert match advancement audit logs"
on public.match_advancement_audit_logs for insert
to authenticated
with check (private.can_manage_tournament(tournament_id));

-- All live policy/function dependencies have now been migrated to explicit
-- admin, organizer or object-capability checks.
drop function if exists private.is_organizer_or_admin();
