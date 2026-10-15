-- Импорт товаров у поставщиков (Zalando: страницы каталога или фид партнёрской программы).
-- Сохраняем факты о товаре; фото и тексты поставщика на витрину не копируем.
create table import_jobs (
    id          bigserial primary key,
    source      varchar(20)   not null,
    kind        varchar(10)   not null,
    url         varchar(1000),
    max_items   integer       not null,
    status      varchar(10)   not null,
    found       integer       not null default 0,
    imported    integer       not null default 0,
    failed      integer       not null default 0,
    message     varchar(1000),
    started_by  varchar(100),
    created_at  timestamptz   not null,
    finished_at timestamptz
);

create table import_items (
    id               bigserial primary key,
    job_id           bigint        references import_jobs (id) on delete set null,
    source           varchar(20)   not null,
    source_url       varchar(1000) not null unique,
    sku              varchar(60),
    brand            varchar(200),
    title            varchar(300)  not null,
    color            varchar(100),
    category_hint    varchar(300),
    price_eur_cents  bigint,
    currency         varchar(3),
    sizes            varchar(500),
    available        boolean       not null default true,
    image_url        varchar(1000),
    status           varchar(10)   not null,
    product_id       bigint,
    markup_percent   numeric(5, 2),
    reject_reason    varchar(300),
    fetched_at       timestamptz   not null,
    updated_at       timestamptz   not null
);
create index import_items_status_idx on import_items (status, id);
