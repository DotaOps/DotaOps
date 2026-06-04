create index if not exists audit_log_created_at_idx
  on public.audit_log(created_at desc, id desc);

comment on index public.audit_log_created_at_idx is
  'Supports newest-first admin audit log pagination and time-range filtering.';
