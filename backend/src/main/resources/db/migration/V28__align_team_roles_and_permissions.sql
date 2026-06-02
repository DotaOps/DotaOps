update public.profiles
set role = 'player'::public.dotaops_user_role,
    updated_at = now()
where role = 'captain'::public.dotaops_user_role;

alter table public.profiles drop constraint if exists profiles_role_no_global_captain;
alter table public.profiles add constraint profiles_role_no_global_captain
  check (role in (
    'player'::public.dotaops_user_role,
    'organizer'::public.dotaops_user_role,
    'admin'::public.dotaops_user_role
  ));

comment on constraint profiles_role_no_global_captain on public.profiles is
  'Persisted profile roles are player, organizer and admin. Team captain is represented by teams.captain_profile_id.';

drop policy if exists "authenticated users create own teams" on public.teams;
create policy "players create own teams"
on public.teams for insert
to authenticated
with check (
  created_by = (select auth.uid())
  and captain_profile_id = (select private.current_profile_id())
  and exists (
    select 1
    from public.profiles p
    where p.id = (select private.current_profile_id())
      and p.role = 'player'::public.dotaops_user_role
  )
);

drop policy if exists "captains and organizers update teams" on public.teams;
create policy "captains and admins update teams"
on public.teams for update
to authenticated
using (
  private.is_team_captain(id)
  or (select private.is_admin())
)
with check (
  private.is_team_captain(id)
  or (select private.is_admin())
);

drop policy if exists "captains insert team members" on public.team_members;
create policy "captains and admins insert team members"
on public.team_members for insert
to authenticated
with check (
  private.is_team_captain(team_id)
  or (select private.is_admin())
);

drop policy if exists "captains update team members" on public.team_members;
create policy "captains and admins update team members"
on public.team_members for update
to authenticated
using (
  private.is_team_captain(team_id)
  or (select private.is_admin())
)
with check (
  private.is_team_captain(team_id)
  or (select private.is_admin())
);

drop policy if exists "captains delete team members" on public.team_members;
create policy "captains and admins delete team members"
on public.team_members for delete
to authenticated
using (
  private.is_team_captain(team_id)
  or (select private.is_admin())
);

drop policy if exists "team invitations are readable by participants" on public.team_invitations;
create policy "team invitations are readable by participants"
on public.team_invitations for select
to authenticated
using (
  private.is_team_captain(team_id)
  or invitee_profile_id = (select private.current_profile_id())
  or (select private.is_admin())
);

drop policy if exists "captains insert team invitations" on public.team_invitations;
create policy "captains and admins insert team invitations"
on public.team_invitations for insert
to authenticated
with check (
  private.is_team_captain(team_id)
  or (select private.is_admin())
);

drop policy if exists "captains update team invitations" on public.team_invitations;
create policy "captains and admins update team invitations"
on public.team_invitations for update
to authenticated
using (
  private.is_team_captain(team_id)
  or (select private.is_admin())
)
with check (
  private.is_team_captain(team_id)
  or (select private.is_admin())
);

drop policy if exists "captains delete team invitations" on public.team_invitations;
create policy "captains and admins delete team invitations"
on public.team_invitations for delete
to authenticated
using (
  private.is_team_captain(team_id)
  or (select private.is_admin())
);

drop policy if exists "captains manage manual players" on public.team_manual_players;
create policy "captains and admins manage manual players"
on public.team_manual_players for all
to authenticated
using (
  private.is_team_captain(team_id)
  or (select private.is_admin())
)
with check (
  private.is_team_captain(team_id)
  or (select private.is_admin())
);

drop policy if exists "team join requests are visible to requester and captains" on public.team_join_requests;
create policy "team join requests are visible to requester captains and admins"
on public.team_join_requests for select
to authenticated
using (
  requester_profile_id = (select private.current_profile_id())
  or private.is_team_captain(team_id)
  or (select private.is_admin())
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
);
