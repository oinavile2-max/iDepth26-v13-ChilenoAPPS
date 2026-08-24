-- iDepth 26 v1.6 — backend incremental
-- Execute no Supabase SQL Editor ou via migrations.
create extension if not exists pgcrypto;

alter table public.wallpapers
  add column if not exists vip_only boolean not null default false;

create table if not exists public.app_events (
  id uuid primary key default gen_random_uuid(),
  install_id text not null,
  event text not null,
  details text default '',
  app_version text default '',
  android_version text default '',
  device text default '',
  created_at timestamptz not null default now()
);

create index if not exists app_events_created_at_idx on public.app_events(created_at desc);
create index if not exists app_events_install_id_idx on public.app_events(install_id);
create index if not exists app_events_event_idx on public.app_events(event);
alter table public.app_events enable row level security;

create table if not exists public.user_wallpaper_submissions (
  id uuid primary key default gen_random_uuid(),
  install_id text default '',
  storage_path text not null,
  original_name text default '',
  mime_type text default '',
  size_bytes bigint default 0,
  status text not null default 'pending'
    check (status in ('pending','approved','rejected','published')),
  created_at timestamptz not null default now(),
  reviewed_at timestamptz,
  notes text default ''
);

create index if not exists user_wallpaper_submissions_status_idx
on public.user_wallpaper_submissions(status, created_at desc);
alter table public.user_wallpaper_submissions enable row level security;

create table if not exists public.email_deliveries (
  id uuid primary key default gen_random_uuid(),
  submission_id uuid references public.user_wallpaper_submissions(id) on delete set null,
  subject text default '',
  status text not null default 'sent' check (status in ('sent','failed')),
  detail text default '',
  created_at timestamptz not null default now()
);

create index if not exists email_deliveries_created_at_idx
on public.email_deliveries(created_at desc);
alter table public.email_deliveries enable row level security;

insert into storage.buckets (id, name, public)
values ('user-submissions', 'user-submissions', false)
on conflict (id) do update set public = false;

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
submission_metrics as (
  select count(*) filter (where status = 'pending')::int as submissions_pending
  from public.user_wallpaper_submissions
),
email_metrics as (
  select count(*) filter (where status = 'sent')::int as emails_sent
  from public.email_deliveries
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
  'submissions_pending', sm.submissions_pending,
  'emails_sent', em.emails_sent,
  'recent_events', re.value,
  'submissions', su.value,
  'personalizations', pe.value,
  'emails', ee.value
)
from metrics m
cross join submission_metrics sm
cross join email_metrics em
cross join recent_events re
cross join submissions su
cross join personalizations pe
cross join emails ee;
$$;

revoke all on function public.admin_dashboard_metrics() from public;
revoke all on function public.admin_dashboard_metrics() from anon;
revoke all on function public.admin_dashboard_metrics() from authenticated;
grant execute on function public.admin_dashboard_metrics() to service_role;

-- Nenhuma policy pública é criada: escrita/leitura ocorre pelas Edge Functions com service role.
