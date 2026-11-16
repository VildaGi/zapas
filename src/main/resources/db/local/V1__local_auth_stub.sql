-- Только для локального Postgres: имитация Supabase auth.users.
create schema if not exists auth;

create table if not exists auth.users (
    id uuid primary key,
    email text
);

insert into auth.users (id, email)
values ('00000000-0000-0000-0000-000000000001', 'local@zapas.dev')
on conflict do nothing;
