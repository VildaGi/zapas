-- Смещение времени для тестовой кнопки «+1 день» во фронте
alter table public.households
    add column if not exists time_offset_days integer not null default 0;

-- Флаг «напоминание уже показано» (поле notified во фронте)
alter table public.stock_items
    add column if not exists notified_at timestamptz;

-- Личные товары домохозяйства: не засоряют общий каталог и безопасно переименовываются
alter table public.products
    add column if not exists household_id uuid references public.households(id) on delete cascade;

create index if not exists products_household_idx
    on public.products(household_id)
    where household_id is not null;

-- Источник позиции списка «закончилось внезапно»
alter table public.list_items drop constraint if exists list_items_source_check;
alter table public.list_items
    add constraint list_items_source_check
    check (source in ('manual', 'reminder', 'spontaneous', 'sudden'));
