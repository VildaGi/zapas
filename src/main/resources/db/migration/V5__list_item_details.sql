-- Количество, единица и заметка позиции списка теперь хранятся на сервере
-- (раньше фронт держал их в localStorage браузера).

-- «Количество не указано» теперь выражается NULL. Раньше API количество никогда не писал,
-- поэтому все существующие 1 — это просто значение по умолчанию.
alter table public.list_items alter column quantity drop not null;
alter table public.list_items alter column quantity drop default;
update public.list_items set quantity = null;

-- Единицы хранятся в том виде, в каком их выбирает пользователь: шт, уп., кг, г, л, мл
alter table public.list_items alter column unit set default 'шт';
update public.list_items set unit = 'шт' where unit = 'pcs';

alter table public.list_items
    add constraint list_items_note_length check (note is null or length(note) <= 200);
