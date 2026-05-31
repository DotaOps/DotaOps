do $$
begin
  create type public.dotaops_notification_type as enum (
    'system',
    'team_application_submitted',
    'team_application_approved',
    'team_application_rejected',
    'match_scheduled'
  );
exception
  when duplicate_object then null;
end;
$$;

alter type public.dotaops_delivery_status add value if not exists 'processing';
alter type public.dotaops_delivery_status add value if not exists 'delivered';

alter table public.notification_outbox
  add column if not exists type public.dotaops_notification_type,
  add column if not exists title text,
  add column if not exists message text,
  add column if not exists attempt_count integer,
  add column if not exists next_attempt_at timestamptz,
  add column if not exists processed_at timestamptz,
  add column if not exists read_at timestamptz;

update public.notification_outbox
set
  type = coalesce(type, 'system'::public.dotaops_notification_type),
  title = coalesce(nullif(title, ''), nullif(subject, ''), 'Notification'),
  message = coalesce(message, body, ''),
  attempt_count = coalesce(attempt_count, attempts, 0),
  next_attempt_at = coalesce(next_attempt_at, available_at, created_at, now()),
  processed_at = coalesce(processed_at, sent_at)
where type is null
   or title is null
   or message is null
   or attempt_count is null
   or next_attempt_at is null
   or (processed_at is null and sent_at is not null);

alter table public.notification_outbox
  alter column type set default 'system',
  alter column type set not null,
  alter column title set not null,
  alter column message set not null,
  alter column attempt_count set default 0,
  alter column attempt_count set not null,
  alter column next_attempt_at set default now(),
  alter column next_attempt_at set not null;

alter table public.notification_outbox
  drop constraint if exists notification_outbox_attempt_count_non_negative,
  add constraint notification_outbox_attempt_count_non_negative check (attempt_count >= 0);

alter table public.notification_outbox
  drop constraint if exists notification_outbox_title_length,
  add constraint notification_outbox_title_length check (char_length(title) between 1 and 200);

alter table public.notification_outbox
  drop constraint if exists notification_outbox_message_length,
  add constraint notification_outbox_message_length check (char_length(message) <= 4000);

alter table public.notification_outbox
  drop constraint if exists notification_outbox_last_error_length,
  add constraint notification_outbox_last_error_length check (last_error is null or char_length(last_error) <= 4000);

create index if not exists notification_outbox_recipient_profile_id_idx
  on public.notification_outbox(recipient_profile_id);

create index if not exists notification_outbox_status_idx
  on public.notification_outbox(status);

create index if not exists notification_outbox_type_idx
  on public.notification_outbox(type);

create index if not exists notification_outbox_created_at_idx
  on public.notification_outbox(created_at desc);

create index if not exists notification_outbox_status_next_attempt_at_idx
  on public.notification_outbox(status, next_attempt_at)
  where status in ('queued', 'failed');

drop policy if exists "users update own notification read markers" on public.notification_outbox;
create policy "users update own notification read markers"
on public.notification_outbox for update
to authenticated
using (recipient_profile_id = (select private.current_profile_id()))
with check (recipient_profile_id = (select private.current_profile_id()));

grant select on public.notification_outbox to authenticated;
grant update (read_at, updated_at) on public.notification_outbox to authenticated;
grant all on public.notification_outbox to service_role;

comment on table public.notification_outbox is
  'Durable notification outbox for in-app notifications and future email/Discord delivery providers.';
