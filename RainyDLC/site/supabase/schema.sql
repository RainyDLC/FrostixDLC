-- ═══════════════════════════════════════════════════════════════
-- RainyDLC · схема базы (выполнить в Supabase → SQL Editor → New query)
-- ═══════════════════════════════════════════════════════════════

-- Профили: 1-к-1 к auth.users. is_admin ставится ТОЛЬКО этим SQL —
-- через сайт получить админку невозможно.
create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  email text,
  is_admin boolean not null default false,
  created_at timestamptz not null default now()
);

-- Настройки сайта (техработы). Одна строка с id = 1.
create table if not exists public.site_settings (
  id int primary key,
  maintenance boolean not null default false,
  maintenance_message text not null default ''
);
insert into public.site_settings (id, maintenance, maintenance_message)
values (1, false, '')
on conflict (id) do nothing;

-- ── RLS ──────────────────────────────────────────────────────
alter table public.profiles enable row level security;
alter table public.site_settings enable row level security;

-- Пользователь видит и правит ТОЛЬКО свой профиль
drop policy if exists "profiles_read_own" on public.profiles;
create policy "profiles_read_own" on public.profiles
  for select using (auth.uid() = id);

drop policy if exists "profiles_update_own" on public.profiles;
create policy "profiles_update_own" on public.profiles
  for update using (auth.uid() = id)
  with check (auth.uid() = id and is_admin = false); -- is_admin через RLS не поднять

-- Флаг техработ виден всем (читает middleware), писать может только service_role
drop policy if exists "settings_read_all" on public.site_settings;
create policy "settings_read_all" on public.site_settings
  for select using (true);

-- ── Триггер: профиль создаётся автоматически при регистрации ──
create or replace function public.handle_new_user()
returns trigger as $$
begin
  insert into public.profiles (id, email)
  values (new.id, new.email)
  on conflict (id) do nothing;
  return new;
end;
$$ language plpgsql security definer;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ── СДЕЛАТЬ СЕБЯ АДМИНОМ (выполни после регистрации, подставь свой email) ──
-- update public.profiles set is_admin = true where email = 'you@example.com';
