-- Собственная аутентификация (email + пароль, серверные сессии).
-- Пароли хранятся только как bcrypt-хеш, токены сессий — только как SHA-256.

create table public.app_users (
    id uuid primary key default gen_random_uuid(),
    email text not null unique check (email = lower(email)),
    password_hash text not null,
    created_at timestamptz not null default now()
);

create table public.auth_sessions (
    id uuid primary key default gen_random_uuid(),
    token_hash text not null unique,
    user_id uuid not null references public.app_users(id) on delete cascade,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null
);

create index auth_sessions_user_idx on public.auth_sessions(user_id);
create index auth_sessions_expires_idx on public.auth_sessions(expires_at);

-- Для Supabase: закрываем таблицы от PostgREST (anon-ключ). Бэкенд подключается
-- напрямую владельцем и RLS обходит.
alter table public.app_users enable row level security;
alter table public.auth_sessions enable row level security;

-- Все внешние ключи public.* -> auth.users переносим на public.app_users.
-- Уже существующих владельцев данных (например, тестового пользователя) копируем
-- как «legacy»-аккаунты без возможности входа (password_hash = '!').
do $$
declare
    r record;
    action text;
begin
    for r in
        select c.conrelid::regclass::text as tbl,
               c.conname,
               a.attname as col,
               c.confdeltype
          from pg_constraint c
          join pg_attribute a on a.attrelid = c.conrelid and a.attnum = c.conkey[1]
         where c.contype = 'f'
           and c.confrelid = 'auth.users'::regclass
           and c.connamespace = 'public'::regnamespace
    loop
        execute format(
            'insert into public.app_users (id, email, password_hash) '
            || 'select distinct %1$I, ''legacy+'' || %1$I::text || ''@zapas.invalid'', ''!'' '
            || 'from %2$s where %1$I is not null on conflict (id) do nothing',
            r.col, r.tbl);

        action := case r.confdeltype
                      when 'c' then 'cascade'
                      when 'n' then 'set null'
                      when 'd' then 'set default'
                      when 'a' then 'no action'
                      else 'restrict'
                  end;

        execute format('alter table %s drop constraint %I', r.tbl, r.conname);
        execute format(
            'alter table %s add constraint %I foreign key (%I) references public.app_users(id) on delete %s',
            r.tbl, r.conname, r.col, action);
    end loop;
end $$;
