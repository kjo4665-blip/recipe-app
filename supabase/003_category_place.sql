-- 분류·가게 칸 (2026-11-27)
alter table public.receipts add column if not exists category text not null default '기타';
alter table public.receipts add column if not exists place text;
