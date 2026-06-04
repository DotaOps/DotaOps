alter table public.profiles
  add column if not exists avatar_path text;

alter table public.teams
  add column if not exists logo_path text,
  add column if not exists banner_path text;

alter table public.profiles
  drop constraint if exists profiles_avatar_path_format;
alter table public.profiles
  add constraint profiles_avatar_path_format
  check (
    avatar_path is null
    or avatar_path ~ '^profiles/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/avatar\.(png|jpg|jpeg|webp)$'
  );

alter table public.teams
  drop constraint if exists teams_logo_path_format;
alter table public.teams
  add constraint teams_logo_path_format
  check (
    logo_path is null
    or logo_path ~ '^teams/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/logo\.(png|jpg|jpeg|webp)$'
  );

alter table public.teams
  drop constraint if exists teams_banner_path_format;
alter table public.teams
  add constraint teams_banner_path_format
  check (
    banner_path is null
    or banner_path ~ '^teams/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/banner\.(png|jpg|jpeg|webp)$'
  );

comment on column public.profiles.avatar_path is
  'Supabase Storage object path for the current profile avatar in the avatars bucket.';
comment on column public.teams.logo_path is
  'Supabase Storage object path for the current team logo in the team-assets bucket.';
comment on column public.teams.banner_path is
  'Supabase Storage object path for the current team banner in the team-assets bucket.';

create or replace function private.storage_profile_avatar_owner(object_name text)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  path_parts text[];
  target_profile_id uuid;
begin
  if object_name is null then
    return false;
  end if;

  path_parts := string_to_array(object_name, '/');
  if array_length(path_parts, 1) <> 3
     or path_parts[1] <> 'profiles'
     or path_parts[3] !~ '^avatar\.(png|jpg|jpeg|webp)$' then
    return false;
  end if;

  begin
    target_profile_id := path_parts[2]::uuid;
  exception
    when invalid_text_representation then
      return false;
  end;

  return exists (
    select 1
    from public.profiles p
    where p.id = target_profile_id
      and p.auth_user_id = (select auth.uid())
  );
end;
$$;

create or replace function private.storage_team_asset_owner(object_name text)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  path_parts text[];
  target_team_id uuid;
begin
  if object_name is null then
    return false;
  end if;

  path_parts := string_to_array(object_name, '/');
  if array_length(path_parts, 1) <> 3
     or path_parts[1] <> 'teams'
     or path_parts[3] !~ '^(logo|banner)\.(png|jpg|jpeg|webp)$' then
    return false;
  end if;

  begin
    target_team_id := path_parts[2]::uuid;
  exception
    when invalid_text_representation then
      return false;
  end;

  return private.is_team_captain(target_team_id);
end;
$$;

comment on function private.storage_profile_avatar_owner(text) is
  'Returns true when the authenticated Supabase user owns the profile avatar path.';
comment on function private.storage_team_asset_owner(text) is
  'Returns true when the authenticated profile is the captain of the team asset path.';

do $$
begin
  if to_regclass('storage.buckets') is not null
     and exists (
       select 1
       from information_schema.columns
       where table_schema = 'storage'
         and table_name = 'buckets'
         and column_name = 'public'
     )
     and exists (
       select 1
       from information_schema.columns
       where table_schema = 'storage'
         and table_name = 'buckets'
         and column_name = 'file_size_limit'
     )
     and exists (
       select 1
       from information_schema.columns
       where table_schema = 'storage'
         and table_name = 'buckets'
         and column_name = 'allowed_mime_types'
     ) then
    insert into storage.buckets (
      id,
      name,
      "public",
      file_size_limit,
      allowed_mime_types
    )
    values
      (
        'avatars',
        'avatars',
        true,
        2097152,
        array['image/png', 'image/jpeg', 'image/webp']::text[]
      ),
      (
        'team-assets',
        'team-assets',
        true,
        5242880,
        array['image/png', 'image/jpeg', 'image/webp']::text[]
      )
    on conflict (id) do update
      set "public" = excluded."public",
          file_size_limit = excluded.file_size_limit,
          allowed_mime_types = excluded.allowed_mime_types;
  end if;
end $$;

do $$
begin
  if to_regclass('storage.objects') is not null then
    execute 'drop policy if exists "dotaops avatars public read" on storage.objects';
    execute 'create policy "dotaops avatars public read"
      on storage.objects for select
      to public
      using (bucket_id = ''avatars'')';

    execute 'drop policy if exists "dotaops avatars owner insert" on storage.objects';
    execute 'create policy "dotaops avatars owner insert"
      on storage.objects for insert
      to authenticated
      with check (
        bucket_id = ''avatars''
        and private.storage_profile_avatar_owner(name)
      )';

    execute 'drop policy if exists "dotaops avatars owner update" on storage.objects';
    execute 'create policy "dotaops avatars owner update"
      on storage.objects for update
      to authenticated
      using (
        bucket_id = ''avatars''
        and private.storage_profile_avatar_owner(name)
      )
      with check (
        bucket_id = ''avatars''
        and private.storage_profile_avatar_owner(name)
      )';

    execute 'drop policy if exists "dotaops avatars owner delete" on storage.objects';
    execute 'create policy "dotaops avatars owner delete"
      on storage.objects for delete
      to authenticated
      using (
        bucket_id = ''avatars''
        and private.storage_profile_avatar_owner(name)
      )';

    execute 'drop policy if exists "dotaops team assets public read" on storage.objects';
    execute 'create policy "dotaops team assets public read"
      on storage.objects for select
      to public
      using (bucket_id = ''team-assets'')';

    execute 'drop policy if exists "dotaops team assets captain insert" on storage.objects';
    execute 'create policy "dotaops team assets captain insert"
      on storage.objects for insert
      to authenticated
      with check (
        bucket_id = ''team-assets''
        and private.storage_team_asset_owner(name)
      )';

    execute 'drop policy if exists "dotaops team assets captain update" on storage.objects';
    execute 'create policy "dotaops team assets captain update"
      on storage.objects for update
      to authenticated
      using (
        bucket_id = ''team-assets''
        and private.storage_team_asset_owner(name)
      )
      with check (
        bucket_id = ''team-assets''
        and private.storage_team_asset_owner(name)
      )';

    execute 'drop policy if exists "dotaops team assets captain delete" on storage.objects';
    execute 'create policy "dotaops team assets captain delete"
      on storage.objects for delete
      to authenticated
      using (
        bucket_id = ''team-assets''
        and private.storage_team_asset_owner(name)
      )';
  end if;
end $$;

drop trigger if exists audit_profiles on public.profiles;
create trigger audit_profiles
after insert or update or delete on public.profiles
for each row execute function private.write_audit_log();
