grant usage on schema private to authenticated;

revoke all on function private.storage_profile_avatar_owner(text) from public, anon;
revoke all on function private.storage_team_asset_owner(text) from public, anon;

grant execute on function private.storage_profile_avatar_owner(text) to authenticated, service_role;
grant execute on function private.storage_team_asset_owner(text) to authenticated, service_role;

comment on function private.storage_profile_avatar_owner(text) is
  'Returns true when the authenticated Supabase user owns the profile avatar path. Callable by authenticated users for storage RLS checks.';
comment on function private.storage_team_asset_owner(text) is
  'Returns true when the authenticated profile is the captain of the team asset path. Callable by authenticated users for storage RLS checks.';
