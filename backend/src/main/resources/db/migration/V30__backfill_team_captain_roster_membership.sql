with captain_members_to_reactivate as (
  select distinct on (tm.team_id, tm.profile_id)
    tm.id
  from public.team_members tm
  join public.teams t
    on t.id = tm.team_id
   and t.captain_profile_id = tm.profile_id
  where not tm.is_active
    and not exists (
      select 1
      from public.team_members active_tm
      where active_tm.team_id = tm.team_id
        and active_tm.profile_id = tm.profile_id
        and active_tm.is_active
    )
  order by
    tm.team_id,
    tm.profile_id,
    tm.updated_at desc,
    tm.joined_at desc,
    tm.id desc
)
update public.team_members tm
set
  is_active = true,
  left_at = null,
  updated_at = now()
from captain_members_to_reactivate candidate
where tm.id = candidate.id;

insert into public.team_members (
  team_id,
  profile_id,
  member_role
)
select
  t.id,
  t.captain_profile_id,
  'support'::public.dotaops_team_member_role
from public.teams t
where t.captain_profile_id is not null
  and not exists (
    select 1
    from public.team_members tm
    where tm.team_id = t.id
      and tm.profile_id = t.captain_profile_id
      and tm.is_active
  )
on conflict do nothing;

comment on column public.teams.captain_profile_id is
  'Team-level owner/captain profile. The captain is also represented as an active team_members roster participant.';
