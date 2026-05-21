alter table public.match_import_events
  add column if not exists error_code text;

alter table public.match_import_events drop constraint if exists match_import_events_error_code_allowed;
alter table public.match_import_events add constraint match_import_events_error_code_allowed
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

create index if not exists match_import_events_error_code_idx
  on public.match_import_events(error_code)
  where error_code is not null;
