-- 결제수단·카드 정보·영수증 사진 보관 (2026-11-27)
alter table public.receipts add column if not exists pay_method text not null default '미확인';
alter table public.receipts add column if not exists card_info text;
alter table public.receipts add column if not exists photo_path text;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('receipts', 'receipts', false, 5242880, array['image/jpeg','image/png','image/webp'])
on conflict (id) do nothing;

-- 사진 경로: {매장id}/{년}/{월}/{영수증id}.jpg
create or replace function private.store_from_path(p text) returns uuid
language sql immutable set search_path = '' as $$
  select case when split_part(p, '/', 1) ~ '^[0-9a-f-]{36}$' then split_part(p, '/', 1)::uuid end
$$;
grant execute on function private.store_from_path(text) to authenticated;

create policy receipt_photos_select on storage.objects for select to authenticated
  using (bucket_id = 'receipts' and (
    private.can_access_store(private.store_from_path(name))
    or exists (select 1 from public.receipts r where r.photo_path = storage.objects.name and private.can_access_store(r.store_id))));
create policy receipt_photos_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'receipts' and private.can_access_store(private.store_from_path(name)));
create policy receipt_photos_update on storage.objects for update to authenticated
  using (bucket_id = 'receipts' and private.can_access_store(private.store_from_path(name)))
  with check (bucket_id = 'receipts' and private.can_access_store(private.store_from_path(name)));
create policy receipt_photos_delete on storage.objects for delete to authenticated
  using (bucket_id = 'receipts' and private.is_admin());
