-- 분류 목록 (관리자가 앱에서 추가·수정·삭제·순서 변경) 2026-11-27
create table if not exists public.categories (
  id uuid primary key default gen_random_uuid(),
  name text not null unique check (char_length(name) between 1 and 12),
  color text not null default '#868E96',
  sort int not null default 0,
  created_at timestamptz not null default now()
);
alter table public.categories enable row level security;
create policy categories_select on public.categories for select to authenticated
  using (exists (select 1 from public.members m where m.email = private.my_email()));
create policy categories_insert on public.categories for insert to authenticated with check (private.is_admin());
create policy categories_update on public.categories for update to authenticated using (private.is_admin()) with check (private.is_admin());
create policy categories_delete on public.categories for delete to authenticated using (private.is_admin() and name <> '기타');
-- 기본 16개는 마이그레이션 적용 시 함께 넣었음
