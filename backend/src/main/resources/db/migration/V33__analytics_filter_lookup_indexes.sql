create index if not exists matches_scheduled_at_idx
  on public.matches(scheduled_at)
  where scheduled_at is not null;

create index if not exists matches_started_at_idx
  on public.matches(started_at)
  where started_at is not null;

create index if not exists matches_finished_at_idx
  on public.matches(finished_at)
  where finished_at is not null;

create index if not exists match_games_started_at_idx
  on public.match_games(started_at)
  where started_at is not null;

create index if not exists match_games_finished_at_idx
  on public.match_games(finished_at)
  where finished_at is not null;

create index if not exists match_imports_requested_at_idx
  on public.match_imports(requested_at)
  where requested_at is not null;

create index if not exists match_imports_completed_at_idx
  on public.match_imports(completed_at)
  where completed_at is not null;

create index if not exists team_members_active_profile_team_idx
  on public.team_members(profile_id, team_id)
  where is_active;
