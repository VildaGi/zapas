create extension if not exists pgcrypto;
create extension if not exists pg_trgm;

create schema if not exists private;

-- =========================================================
-- 1. PROFILES
-- Supabase Auth остаётся источником аутентификации.
-- =========================================================

create table public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    display_name text,
    avatar_url text,
    phone text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- =========================================================
-- 2. HOUSEHOLDS AND MEMBERS
-- =========================================================

create table public.households (
    id uuid primary key default gen_random_uuid(),
    name text not null check (length(trim(name)) > 0),
    created_by uuid not null references auth.users(id) on delete restrict,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.household_members (
    household_id uuid not null references public.households(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete restrict,
    role text not null default 'member'
        check (role in ('owner', 'admin', 'member')),
    joined_at timestamptz not null default now(),
    primary key (household_id, user_id)
);

create index household_members_user_idx
    on public.household_members(user_id, household_id);

-- =========================================================
-- 3. PRODUCT CATALOG
-- =========================================================

create table public.products (
    id uuid primary key default gen_random_uuid(),
    name text not null check (length(trim(name)) > 0),
    brand text,
    category text,
    barcode text,
    image_url text,
    default_unit text not null default 'pcs',
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index products_barcode_uidx
    on public.products(barcode)
    where barcode is not null;

create index products_name_idx
    on public.products using gin (name gin_trgm_ops);

create index products_category_idx
    on public.products(category);

-- =========================================================
-- 4. STORES AND PRODUCT OFFERS
-- =========================================================

create table public.stores (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    website_url text,
    country_code text,
    created_at timestamptz not null default now()
);

create table public.product_offers (
    id uuid primary key default gen_random_uuid(),
    product_id uuid not null references public.products(id) on delete restrict,
    store_id uuid not null references public.stores(id) on delete restrict,
    url text not null,
    external_id text,
    currency text not null check (currency ~ '^[A-Z]{3}$'),
    current_price numeric(12,2) check (current_price >= 0),
    regular_price numeric(12,2) check (regular_price >= 0),
    availability text not null default 'unknown'
        check (availability in ('available', 'unavailable', 'unknown')),
    last_checked_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (store_id, url)
);

create index product_offers_product_idx
    on public.product_offers(product_id, store_id);

create index product_offers_checked_idx
    on public.product_offers(last_checked_at desc);

create table public.offer_price_history (
    id bigint generated always as identity primary key,
    offer_id uuid not null references public.product_offers(id) on delete cascade,
    price numeric(12,2) not null check (price >= 0),
    currency text not null check (currency ~ '^[A-Z]{3}$'),
    observed_at timestamptz not null default now(),
    source text not null default 'parser',
    unique (offer_id, observed_at)
);

create index offer_price_history_offer_time_idx
    on public.offer_price_history(offer_id, observed_at desc);

-- =========================================================
-- 5. STOCK SETTINGS AND STOCK MOVEMENTS
-- =========================================================

create table public.stock_items (
    id uuid primary key default gen_random_uuid(),
    household_id uuid not null references public.households(id) on delete cascade,
    product_id uuid not null references public.products(id) on delete restrict,
    quantity numeric(12,3) not null default 0 check (quantity >= 0),
    unit text not null default 'pcs',
    low_stock_threshold numeric(12,3) not null default 0
        check (low_stock_threshold >= 0),
    expected_duration_days integer
        check (expected_duration_days is null or expected_duration_days > 0),
    reminder_before_days integer not null default 2
        check (reminder_before_days >= 0),
    responsible_user_id uuid references auth.users(id) on delete set null,
    last_restocked_at timestamptz,
    estimated_runout_at timestamptz,
    is_active boolean not null default true,
    notes text,
    created_by uuid not null references auth.users(id) on delete restrict,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (household_id, product_id)
);

create index stock_items_household_active_idx
    on public.stock_items(household_id, is_active);

create index stock_items_responsible_idx
    on public.stock_items(responsible_user_id)
    where responsible_user_id is not null;

create index stock_items_runout_idx
    on public.stock_items(household_id, estimated_runout_at)
    where is_active = true;

create table public.stock_movements (
    id uuid primary key default gen_random_uuid(),
    household_id uuid not null references public.households(id) on delete cascade,
    stock_item_id uuid not null references public.stock_items(id) on delete restrict,
    movement_type text not null
        check (movement_type in ('restock', 'consume', 'adjustment', 'waste')),
    quantity_delta numeric(12,3) not null
        check (quantity_delta <> 0),
    unit text not null,
    purchase_id uuid, -- FK добавим после создания purchases
    note text,
    created_by uuid not null references auth.users(id) on delete restrict,
    created_at timestamptz not null default now()
);

create index stock_movements_stock_time_idx
    on public.stock_movements(stock_item_id, created_at desc);

create index stock_movements_household_time_idx
    on public.stock_movements(household_id, created_at desc);

-- =========================================================
-- 6. SHOPPING LISTS AND ITEMS
-- =========================================================

create table public.shopping_lists (
    id uuid primary key default gen_random_uuid(),
    household_id uuid not null references public.households(id) on delete cascade,
    name text not null default 'Покупки',
    is_archived boolean not null default false,
    created_by uuid not null references auth.users(id) on delete restrict,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index shopping_lists_household_idx
    on public.shopping_lists(household_id, is_archived);

create table public.list_items (
    id uuid primary key default gen_random_uuid(),
    list_id uuid not null references public.shopping_lists(id) on delete cascade,
    product_id uuid references public.products(id) on delete restrict,
    stock_item_id uuid references public.stock_items(id) on delete set null,
    name text not null check (length(trim(name)) > 0),
    quantity numeric(12,3) not null default 1 check (quantity > 0),
    unit text not null default 'pcs',
    status text not null default 'needed'
        check (status in ('needed', 'purchased', 'cancelled')),
    source text not null default 'manual'
        check (source in ('manual', 'reminder', 'spontaneous')),
    added_by uuid not null references auth.users(id) on delete restrict,
    purchased_by uuid references auth.users(id) on delete set null,
    purchased_at timestamptz,
    note text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check (
        (status = 'purchased' and purchased_at is not null)
        or status <> 'purchased'
    )
);

create index list_items_list_status_idx
    on public.list_items(list_id, status, created_at);

create index list_items_product_idx
    on public.list_items(product_id)
    where product_id is not null;

-- =========================================================
-- 7. PURCHASE HISTORY
-- =========================================================

create table public.purchases (
    id uuid primary key default gen_random_uuid(),
    household_id uuid not null references public.households(id) on delete cascade,
    product_id uuid references public.products(id) on delete restrict,
    store_id uuid references public.stores(id) on delete set null,
    list_item_id uuid references public.list_items(id) on delete set null,
    product_name text not null,
    quantity numeric(12,3) not null default 1 check (quantity > 0),
    unit text not null default 'pcs',
    unit_price numeric(12,2) check (unit_price >= 0),
    total_price numeric(12,2) check (total_price >= 0),
    currency text not null check (currency ~ '^[A-Z]{3}$'),
    purchased_by uuid not null references auth.users(id) on delete restrict,
    purchased_at timestamptz not null default now(),
    source text not null default 'manual'
        check (source in ('manual', 'list', 'import')),
    receipt_reference text,
    note text,
    created_at timestamptz not null default now()
);

create index purchases_household_date_idx
    on public.purchases(household_id, purchased_at desc);

create index purchases_product_date_idx
    on public.purchases(household_id, product_id, purchased_at desc);

create index purchases_buyer_date_idx
    on public.purchases(purchased_by, purchased_at desc);

alter table public.stock_movements
    add constraint stock_movements_purchase_fk
    foreign key (purchase_id)
    references public.purchases(id) on delete set null;

-- =========================================================
-- 8. EVENTS / HYPOTHESIS MEASUREMENT
-- =========================================================

create table public.events (
    id bigint generated always as identity primary key,
    event_key uuid not null default gen_random_uuid() unique,
    household_id uuid not null references public.households(id) on delete cascade,
    actor_user_id uuid references auth.users(id) on delete set null,
    stock_item_id uuid references public.stock_items(id) on delete set null,
    list_item_id uuid references public.list_items(id) on delete set null,
    event_type text not null check (
        event_type in (
            'reminder_shown',
            'reminder_opened',
            'bought_early',
            'ran_out_suddenly',
            'stock_updated',
            'item_added',
            'item_purchased'
        )
    ),
    occurred_at timestamptz not null default now(),
    properties jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index events_household_type_time_idx
    on public.events(household_id, event_type, occurred_at desc);

create index events_stock_time_idx
    on public.events(stock_item_id, occurred_at desc)
    where stock_item_id is not null;

-- =========================================================
-- 9. SHARES AND INVITATIONS
-- =========================================================

create table public.shares (
    id uuid primary key default gen_random_uuid(),
    from_user uuid not null references auth.users(id) on delete restrict,
    to_user uuid references auth.users(id) on delete set null,
    product_id uuid not null references public.products(id) on delete restrict,
    share_token_hash text unique,
    status text not null default 'created'
        check (status in ('created', 'opened', 'accepted', 'revoked', 'expired')),
    expires_at timestamptz,
    created_at timestamptz not null default now(),
    opened_at timestamptz,
    accepted_at timestamptz
);

create index shares_sender_idx on public.shares(from_user, created_at desc);
create index shares_recipient_idx on public.shares(to_user, created_at desc)
    where to_user is not null;

create table public.household_invitations (
    id uuid primary key default gen_random_uuid(),
    household_id uuid not null references public.households(id) on delete cascade,
    invited_by uuid not null references auth.users(id) on delete restrict,
    invitee_email text,
    token_hash text not null unique,
    role text not null default 'member'
        check (role in ('admin', 'member')),
    status text not null default 'pending'
        check (status in ('pending', 'accepted', 'revoked', 'expired')),
    expires_at timestamptz not null default (now() + interval '7 days'),
    accepted_by uuid references auth.users(id) on delete set null,
    accepted_at timestamptz,
    created_at timestamptz not null default now()
);

create index household_invitations_household_idx
    on public.household_invitations(household_id, status);
