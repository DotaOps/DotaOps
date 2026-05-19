alter table public.match_imports
  add column if not exists error_code text;

alter table public.match_imports drop constraint if exists match_imports_error_code_allowed;
alter table public.match_imports add constraint match_imports_error_code_allowed
  check (
    error_code is null
    or error_code in (
      'MATCH_NOT_FOUND',
      'RATE_LIMITED',
      'PROVIDER_UNAVAILABLE',
      'PROVIDER_TIMEOUT',
      'INVALID_PROVIDER_RESPONSE'
    )
  );

create or replace view public.public_match_import_status
with (security_invoker = true)
as
select
  mi.id,
  mi.match_id,
  mi.dota_match_id,
  mi.status,
  mi.created_at,
  mi.updated_at,
  mi.match_game_id,
  mi.error_code
from public.match_imports mi
join public.matches m on m.id = mi.match_id
join public.tournaments t on t.id = m.tournament_id
where t.is_public;

grant select on public.public_match_import_status to anon, authenticated, service_role;
