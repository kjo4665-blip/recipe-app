-- 영수증 앱: 매장·직원·권한 구조 (2026-11-27)
-- Supabase SQL Editor에 전체를 붙여넣고 Run 하세요. 한 번만 실행합니다.

create schema if not exists private;
grant usage on schema private to authenticated;

create table public.stores (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(name) between 1 and 40),
  color text not null default '#2B59E8',
  created_at timestamptz not null default now()
);

create table public.members (
  id uuid primary key default gen_random_uuid(),
  email text not null unique check (email = lower(email)),
  name text not null check (char_length(name) between 1 and 30),
  role text not null default 'staff' check (role in ('admin','staff')),
  created_at timestamptz not null default now()
);

create table public.member_stores (
  member_id uuid not null references public.members(id) on delete cascade,
  store_id uuid not null references public.stores(id) on delete cascade,
  primary key (member_id, store_id)
);

alter table public.receipts
  add column store_id uuid references public.stores(id) on delete restrict,
  add column created_by_name text,
  add column deleted_at timestamptz;

create or replace function private.my_email() returns text
language sql stable set search_path = '' as $$
  select lower(coalesce(auth.jwt() ->> 'email', ''))
$$;

create or replace function private.is_admin() returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.members m
                 where m.email = private.my_email() and m.role = 'admin')
$$;

create or replace function private.can_access_store(sid uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select private.is_admin() or exists (
    select 1 from public.member_stores ms
    join public.members m on m.id = ms.member_id
    where m.email = private.my_email() and ms.store_id = sid)
$$;

create or replace function private.receipts_before_insert() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  new.user_id := coalesce(auth.uid(), new.user_id);
  new.created_by_name := (select m.name from public.members m
                          where m.email = private.my_email() and m.role = 'staff');
  return new;
end $$;

grant execute on function private.my_email(), private.is_admin(), private.can_access_store(uuid) to authenticated;
revoke execute on function private.receipts_before_insert() from public, anon, authenticated;

create trigger receipts_before_insert before insert on public.receipts
  for each row execute function private.receipts_before_insert();

insert into public.stores (name, color) values
  ('레벨업까치산역점', '#2B59E8'),
  ('레벨업천안청당점', '#1E9E6A');
update public.receipts
  set store_id = (select id from public.stores where name = '레벨업까치산역점')
  where store_id is null;
alter table public.receipts alter column store_id set not null;
insert into public.members (email, name, role) values ('kjo4665@gmail.com', '관리자', 'admin');

drop policy "own receipts" on public.receipts;
create policy receipts_select on public.receipts for select to authenticated
  using (private.can_access_store(store_id));
create policy receipts_insert on public.receipts for insert to authenticated
  with check (private.can_access_store(store_id) and user_id = auth.uid());
create policy receipts_update on public.receipts for update to authenticated
  using (private.is_admin() or (user_id = auth.uid() and private.can_access_store(store_id)))
  with check (private.can_access_store(store_id));
create policy receipts_delete on public.receipts for delete to authenticated
  using (private.is_admin());
create index receipts_store_date_idx on public.receipts (store_id, date);

alter table public.stores enable row level security;
create policy stores_select on public.stores for select to authenticated
  using (private.can_access_store(id));
create policy stores_insert on public.stores for insert to authenticated with check (private.is_admin());
create policy stores_update on public.stores for update to authenticated using (private.is_admin()) with check (private.is_admin());
create policy stores_delete on public.stores for delete to authenticated using (private.is_admin());

alter table public.members enable row level security;
create policy members_select on public.members for select to authenticated
  using (private.is_admin() or email = private.my_email());
create policy members_insert on public.members for insert to authenticated with check (private.is_admin());
create policy members_update on public.members for update to authenticated using (private.is_admin()) with check (private.is_admin());
create policy members_delete on public.members for delete to authenticated using (private.is_admin() and email <> private.my_email());

alter table public.member_stores enable row level security;
create policy ms_select on public.member_stores for select to authenticated
  using (private.is_admin() or member_id in (select id from public.members where email = private.my_email()));
create policy ms_insert on public.member_stores for insert to authenticated with check (private.is_admin());
create policy ms_delete on public.member_stores for delete to authenticated using (private.is_admin());
