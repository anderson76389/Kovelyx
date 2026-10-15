-- Kovelyx Cloud v1 : account-isolated data model for a future, independent backend.
-- Apply ONLY to a Supabase project owned and administered by Kovelyx.
-- Authentication (email confirmation, tokens, password reset) is provided by
-- that project's Supabase Auth. Do not copy or import existing Nuvio accounts.
-- This migration DOES NOT connect the current apps automatically.
begin;

create table public.kovelyx_account_preferences (
  account_id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default '',
  active_profile_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint kovelyx_account_display_name_limit check (char_length(display_name) <= 64)
);

create table public.kovelyx_profiles (
  id uuid primary key default gen_random_uuid(),
  account_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  display_name text not null,
  avatar_storage_path text,
  banner_storage_path text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint kovelyx_profile_name_limit check (char_length(trim(display_name)) between 1 and 64),
  constraint kovelyx_avatar_path_limit check (
    avatar_storage_path is null or char_length(avatar_storage_path) <= 512
  ),
  constraint kovelyx_banner_path_limit check (
    banner_storage_path is null or char_length(banner_storage_path) <= 512
  ),
  unique(account_id, id)
);

-- This FK also ensures active_profile_id always belongs to the account.
alter table public.kovelyx_account_preferences
  add constraint kovelyx_active_profile_same_account
  foreign key (account_id, active_profile_id)
  references public.kovelyx_profiles(account_id, id)
  deferrable initially immediate;

create table public.kovelyx_sync_items (
  account_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  profile_id uuid not null,
  category text not null,
  item_key text not null,
  payload jsonb not null default '{}'::jsonb,
  revision bigint not null default 1,
  is_deleted boolean not null default false,
  updated_at timestamptz not null default now(),
  primary key (account_id, profile_id, category, item_key),
  constraint kovelyx_sync_profile_fk foreign key (account_id, profile_id)
    references public.kovelyx_profiles(account_id, id) on delete cascade,
  constraint kovelyx_sync_category_check check (
    category in ('catalogues', 'library', 'watch_progress', 'addons',
                 'preferences', 'profile_settings', 'favorites', 'plugins')
  ),
  constraint kovelyx_sync_item_key_check check (char_length(item_key) between 1 and 200),
  constraint kovelyx_sync_revision_check check (revision > 0),
  constraint kovelyx_sync_max_payload_bytes check (
    octet_length(payload::text) <= 131072
  )
);

create index kovelyx_sync_changes_idx
  on public.kovelyx_sync_items(account_id, profile_id, updated_at);

-- Never allow client apps to write arbitrary timestamps or revisions.
-- Revision is always bumped by the database to support optimistic sync.
create or replace function public.kovelyx_set_sync_revision()
returns trigger language plpgsql set search_path = ''
as $$
begin
  if tg_op = 'UPDATE' then
    new.revision := old.revision + 1;
  else
    new.revision := 1;
  end if;
  new.updated_at := now();
  return new;
end;
$$;

create trigger kovelyx_sync_revision_before_write
  before insert or update on public.kovelyx_sync_items
  for each row execute function public.kovelyx_set_sync_revision();

create or replace function public.kovelyx_touch_updated_at()
returns trigger language plpgsql set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

create trigger kovelyx_profiles_touch
  before update on public.kovelyx_profiles
  for each row execute function public.kovelyx_touch_updated_at();

create trigger kovelyx_account_preferences_touch
  before update on public.kovelyx_account_preferences
  for each row execute function public.kovelyx_touch_updated_at();

alter table public.kovelyx_account_preferences enable row level security;
alter table public.kovelyx_profiles enable row level security;
alter table public.kovelyx_sync_items enable row level security;

create policy kv_account_read on public.kovelyx_account_preferences
  for select to authenticated using (account_id = (select auth.uid()));
create policy kv_account_create on public.kovelyx_account_preferences
  for insert to authenticated with check (account_id = (select auth.uid()));
create policy kv_account_update on public.kovelyx_account_preferences
  for update to authenticated using (account_id = (select auth.uid()))
  with check (account_id = (select auth.uid()));
create policy kv_account_delete on public.kovelyx_account_preferences
  for delete to authenticated using (account_id = (select auth.uid()));

create policy kv_profile_read on public.kovelyx_profiles
  for select to authenticated using (account_id = (select auth.uid()));
create policy kv_profile_create on public.kovelyx_profiles
  for insert to authenticated with check (account_id = (select auth.uid()));
create policy kv_profile_update on public.kovelyx_profiles
  for update to authenticated using (account_id = (select auth.uid()))
  with check (account_id = (select auth.uid()));
create policy kv_profile_delete on public.kovelyx_profiles
  for delete to authenticated using (account_id = (select auth.uid()));

create policy kv_sync_read on public.kovelyx_sync_items
  for select to authenticated using (account_id = (select auth.uid()));
create policy kv_sync_create on public.kovelyx_sync_items
  for insert to authenticated with check (account_id = (select auth.uid()));
create policy kv_sync_update on public.kovelyx_sync_items
  for update to authenticated using (account_id = (select auth.uid()))
  with check (account_id = (select auth.uid()));
create policy kv_sync_delete on public.kovelyx_sync_items
  for delete to authenticated using (account_id = (select auth.uid()));

-- Prevent clients from bypassing the intended owner-based model with
-- broad anon access. No service_role key must ever be shipped in an APK.
revoke all on public.kovelyx_account_preferences from anon;
revoke all on public.kovelyx_profiles from anon;
revoke all on public.kovelyx_sync_items from anon;
grant select, insert, update, delete on public.kovelyx_account_preferences to authenticated;
grant select, insert, update, delete on public.kovelyx_profiles to authenticated;
grant select, insert, update, delete on public.kovelyx_sync_items to authenticated;

comment on table public.kovelyx_sync_items is
  'Per-account, per-profile JSON sync records. Never store passwords or API tokens unencrypted.';

commit;
