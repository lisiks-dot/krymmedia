-- Фото и видео товаров, отзывы с подтверждённой покупкой, чат покупателя и продавца.

-- Медиа товара: файлы лежат на диске (том media), в базе — описание и порядок показа.
create table product_media (
    id           bigserial primary key,
    product_id   bigint       not null references products (id) on delete cascade,
    kind         varchar(10)  not null,
    content_type varchar(40)  not null,
    size_bytes   bigint       not null,
    storage_key  varchar(200) not null unique,
    position     integer      not null,
    uploaded_by  varchar(20)  not null,
    created_at   timestamptz  not null
);
create index product_media_product_idx on product_media (product_id, position);

-- Отзыв: один покупатель — один отзыв на товар; только после получения заказа с этим товаром.
create table reviews (
    id           bigserial primary key,
    product_id   bigint        not null references products (id) on delete cascade,
    buyer_sub    varchar(64)   not null,
    author_name  varchar(100)  not null,
    order_number varchar(20)   not null,
    size         varchar(20),
    rating       smallint      not null check (rating between 1 and 5),
    text         varchar(2000) not null,
    hidden       boolean       not null default false,
    hidden_reason varchar(300),
    created_at   timestamptz   not null,
    unique (product_id, buyer_sub)
);
create index reviews_product_idx on reviews (product_id, created_at desc);

create table review_replies (
    id          bigserial primary key,
    review_id   bigint        not null references reviews (id) on delete cascade,
    author_role varchar(10)   not null,
    author_name varchar(100)  not null,
    text        varchar(2000) not null,
    created_at  timestamptz   not null
);
create index review_replies_review_idx on review_replies (review_id, id);

-- Чат: диалог покупателя с продавцом по товару (или с поддержкой ilpacco, если товар площадки).
create table conversations (
    id              bigserial primary key,
    buyer_sub       varchar(64)  not null,
    buyer_name      varchar(100) not null,
    seller_id       bigint,
    seller_name     varchar(200) not null,
    product_id      bigint,
    product_title   varchar(300),
    order_number    varchar(20),
    created_at      timestamptz  not null,
    last_message_at timestamptz  not null,
    last_message    varchar(300),
    buyer_unread    integer      not null default 0,
    seller_unread   integer      not null default 0,
    staff_unread    integer      not null default 0
);
create unique index conversations_unique_idx on conversations (buyer_sub, coalesce(seller_id, 0), coalesce(product_id, 0));
create index conversations_buyer_idx on conversations (buyer_sub, last_message_at desc);
create index conversations_seller_idx on conversations (seller_id, last_message_at desc);

create table messages (
    id              bigserial primary key,
    conversation_id bigint        not null references conversations (id) on delete cascade,
    author_role     varchar(10)   not null,
    author_name     varchar(100)  not null,
    text            varchar(4000) not null,
    masked          boolean       not null default false,
    created_at      timestamptz   not null
);
create index messages_conversation_idx on messages (conversation_id, id);
