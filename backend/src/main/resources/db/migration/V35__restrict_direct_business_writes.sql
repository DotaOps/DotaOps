-- DotaOps business state is written through the Spring Boot database connection.
-- Browser-facing Data API roles remain read-only where an explicit SELECT grant exists.

-- Remove the narrow browser write grants that were added after earlier table-level revokes.
revoke insert (
  auth_user_id,
  nickname,
  display_name,
  steam_id,
  avatar_url,
  bio,
  country_code
) on table public.profiles from public, anon, authenticated;

revoke update (
  nickname,
  display_name,
  steam_id,
  avatar_url,
  bio,
  country_code
) on table public.profiles from public, anon, authenticated;

revoke update (
  read_at,
  updated_at
) on table public.notification_outbox from public, anon, authenticated;

-- Revoke the complete direct business-DML surface from browser Data API roles.
-- Keep this list explicit so Flyway's own history table and Storage-owned tables are untouched.
revoke insert, update, delete on table
  public.profiles,
  public.profile_external_accounts,
  public.teams,
  public.team_members,
  public.team_invitations,
  public.team_join_requests,
  public.team_manual_players,
  public.tournaments,
  public.tournament_registrations,
  public.tournament_registration_members,
  public.tournament_staff,
  public.tournament_groups,
  public.tournament_group_teams,
  public.matches,
  public.match_slots,
  public.match_games,
  public.match_players,
  public.match_imports,
  public.match_import_events,
  public.match_advancement_audit_logs,
  public.heroes,
  public.notification_outbox,
  public.audit_log
from public, anon, authenticated;

-- Browser clients do not need sequence privileges for UUID-backed business writes.
revoke all privileges on all sequences in schema public from public, anon, authenticated;

-- Future objects created by the migration owner are opt-in for browser writes.
alter default privileges in schema public
  revoke insert, update, delete on tables from public, anon, authenticated;

alter default privileges in schema public
  revoke all privileges on sequences from public, anon, authenticated;
