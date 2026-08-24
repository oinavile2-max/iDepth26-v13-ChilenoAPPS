-- iDepth 26 v1.6.1 — acesso VIP administrado pelo painel
-- Execute depois da migration v1.6.

create table if not exists public.vip_overrides (
  install_id text primary key,
  enabled boolean not null default false,
  expires_at timestamptz,
  note text default '',
  updated_at timestamptz not null default now()
);

create index if not exists vip_overrides_enabled_idx
on public.vip_overrides(enabled, updated_at desc);

alter table public.vip_overrides enable row level security;

create or replace function public.admin_dashboard_metrics()
returns jsonb
language sql
security definer
set search_path = public
as $$
with
metrics as (
  select
    count(distinct install_id)::int as users_total,
    count(distinct install_id) filter (where created_at >= now() - interval '24 hours')::int as users_24h,
    count(*) filter (where event = 'wallpaper_apply')::int as wallpapers_applied,
    count(distinct install_id) filter (where event = 'vip_purchase')::int as vip_total
  from public.app_events
),
admin_vip as (
  select count(*)::int as vip_admin_total
  from public.vip_overrides
  where enabled = true and (expires_at is null or expires_at > now())
),
submission_metrics as (
  select count(*) filter (where status = 'pending')::int as submissions_pending
  from public.user_wallpaper_submissions
),
email_metrics as (
  select count(*) filter (where status = 'sent')::int as emails_sent
  from public.email_deliveries
),
latest_users as (
  select distinct on (install_id)
    install_id,
    app_version,
    created_at as last_seen
  from public.app_events
  where install_id <> ''
  order by install_id, created_at desc
),
users as (
  select coalesce(jsonb_agg(to_jsonb(x)), '[]'::jsonb) value
  from (
    select
      u.install_id,
      u.app_version,
      u.last_seen,
      coalesce(v.enabled and (v.expires_at is null or v.expires_at > now()), false) as vip_enabled,
      v.expires_at as vip_expires_at
    from latest_users u
    left join public.vip_overrides v on v.install_id = u.install_id
    order by u.last_seen desc
    limit 50
  ) x
),
recent_events as (
  select coalesce(jsonb_agg(to_jsonb(x)), '[]'::jsonb) value
  from (
    select event, details, app_version, created_at
    from public.app_events
    order by created_at desc
    limit 30
  ) x
),
submissions as (
  select coalesce(jsonb_agg(to_jsonb(x)), '[]'::jsonb) value
  from (
    select id, install_id, storage_path, original_name, mime_type, size_bytes, status, created_at, reviewed_at
    from public.user_wallpaper_submissions
    order by created_at desc
    limit 30
  ) x
),
personalizations as (
  select coalesce(jsonb_agg(to_jsonb(x)), '[]'::jsonb) value
  from (
    select event, details, created_at
    from public.app_events
    where event like 'personalize_%'
    order by created_at desc
    limit 30
  ) x
),
emails as (
  select coalesce(jsonb_agg(to_jsonb(x)), '[]'::jsonb) value
  from (
    select submission_id, subject, status, created_at
    from public.email_deliveries
    order by created_at desc
    limit 30
  ) x
)
select jsonb_build_object(
  'users_total', m.users_total,
  'users_24h', m.users_24h,
  'wallpapers_applied', m.wallpapers_applied,
  'vip_total', m.vip_total,
  'vip_admin_total', av.vip_admin_total,
  'submissions_pending', sm.submissions_pending,
  'emails_sent', em.emails_sent,
  'users', us.value,
  'recent_events', re.value,
  'submissions', su.value,
  'personalizations', pe.value,
  'emails', ee.value
)
from metrics m
cross join admin_vip av
cross join submission_metrics sm
cross join email_metrics em
cross join users us
cross join recent_events re
cross join submissions su
cross join personalizations pe
cross join emails ee;
$$;

revoke all on function public.admin_dashboard_metrics() from public;
revoke all on function public.admin_dashboard_metrics() from anon;
revoke all on function public.admin_dashboard_metrics() from authenticated;
grant execute on function public.admin_dashboard_metrics() to service_role;
