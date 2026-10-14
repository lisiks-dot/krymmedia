create table compliance_rules (
    id                   integer primary key,
    threshold_eur_cents  bigint       not null,
    threshold_categories varchar(200) not null,
    embargo_prefixes     varchar(500) not null,
    alcohol_prefixes     varchar(500) not null,
    updated_at           timestamptz  not null
);

create table products (
    id              bigserial primary key,
    seller_name     varchar(200) not null,
    title           varchar(300) not null,
    region          varchar(100),
    category        varchar(20)  not null,
    hs_code         varchar(10),
    price_eur_cents bigint       not null check (price_eur_cents > 0),
    weight_grams    integer      not null check (weight_grams > 0),
    sizes           varchar(200),
    description     text,
    aerosol         boolean      not null default false,
    status          varchar(20)  not null,
    block_reason    varchar(500),
    created_at      timestamptz  not null default now()
);
create index products_status_category_idx on products (status, category);

create sequence order_number_seq start 10500;

create table orders (
    id              bigserial primary key,
    number          varchar(20)  not null unique,
    status          varchar(30)  not null,
    delivery_mode   varchar(10)  not null,
    buyer_name      varchar(200) not null,
    buyer_phone     varchar(30)  not null,
    passport_masked varchar(20)  not null,
    pickup_point    varchar(300) not null,
    goods_rub       bigint       not null,
    delivery_rub    bigint       not null,
    duty_rub        bigint       not null,
    vat_rub         bigint       not null,
    total_rub       bigint       not null,
    created_at      timestamptz  not null,
    updated_at      timestamptz  not null
);

create table order_lines (
    order_id        bigint       not null references orders (id) on delete cascade,
    product_id      bigint       not null,
    title           varchar(300) not null,
    seller_name     varchar(200) not null,
    category        varchar(20)  not null,
    size            varchar(20),
    qty             integer      not null check (qty > 0),
    price_eur_cents bigint       not null
);
create index order_lines_order_idx on order_lines (order_id);
