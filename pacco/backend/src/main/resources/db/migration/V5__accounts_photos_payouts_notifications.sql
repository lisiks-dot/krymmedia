-- Кабинет покупателя, пункты выдачи на карте, фото с хаба и из спора, реестры выплат, уведомления.

-- Покупатель с аккаунтом: sub из Keycloak. Заказы без аккаунта по-прежнему открываются ключом.
alter table orders add column buyer_sub       varchar(64);
alter table orders add column buyer_email     varchar(200);
alter table orders add column pickup_point_id bigint;
create index orders_buyer_idx on orders (buyer_sub);

create table buyer_profiles (
    sub             varchar(64)  primary key,
    name            varchar(200),
    phone           varchar(20),
    email           varchar(200),
    pickup_point_id bigint,
    notify_sms      boolean      not null default true,
    notify_email    boolean      not null default true,
    updated_at      timestamptz  not null
);

-- Пункты выдачи. Демо-набор: адреса и координаты приблизительные, в рабочей системе список приходит из API перевозчиков.
create table pickup_points (
    id       bigserial primary key,
    carrier  varchar(30)      not null,
    city     varchar(60)      not null,
    address  varchar(200)     not null,
    lat      double precision not null,
    lng      double precision not null,
    hours    varchar(100)     not null,
    note     varchar(200),
    fitting  boolean          not null default false,
    active   boolean          not null default true
);
create index pickup_points_city_idx on pickup_points (city) where active;

insert into pickup_points (carrier, city, address, lat, lng, hours, note, fitting) values
('СДЭК',         'Москва',          'ул. Покровка, 12',            55.7590, 37.6440, 'Ежедневно 10:00–21:00', '5 минут от м. Китай-город', true),
('Boxberry',     'Москва',          'Ленинградский пр-т, 39',      55.7930, 37.5440, 'Пн–Сб 09:00–20:00',     'м. Динамо', true),
('СДЭК',         'Москва',          'ул. Профсоюзная, 56',         55.6700, 37.5530, 'Ежедневно 09:00–21:00', 'м. Новые Черёмушки', false),
('Почта России', 'Москва',          'ул. Мясницкая, 26',           55.7650, 37.6380, 'Пн–Пт 08:00–20:00',     'Главпочтамт', false),
('СДЭК',         'Москва',          'Кутузовский пр-т, 24',        55.7430, 37.5440, 'Ежедневно 10:00–22:00', 'м. Кутузовская', true),
('Boxberry',     'Москва',          'ул. Бутырская, 46',           55.8000, 37.5850, 'Пн–Вс 10:00–20:00',     'м. Савеловская', false),
('Почта России', 'Санкт-Петербург', 'Невский пр-т, 6',             59.9365, 30.3150, 'Пн–Пт 08:00–20:00',     'У Адмиралтейства', false),
('СДЭК',         'Санкт-Петербург', 'ул. Рубинштейна, 15',         59.9290, 30.3460, 'Ежедневно 10:00–21:00', 'м. Достоевская', true),
('Boxberry',     'Санкт-Петербург', 'Московский пр-т, 73',         59.8960, 30.3180, 'Пн–Сб 09:00–20:00',     'м. Фрунзенская', false),
('СДЭК',         'Казань',          'ул. Баумана, 44',             55.7900, 49.1150, 'Ежедневно 10:00–20:00', 'Пешеходная улица, вход со двора', true),
('Boxberry',     'Казань',          'пр-т Победы, 141',            55.7570, 49.2160, 'Пн–Сб 10:00–20:00',     'ТЦ «Мега»', false),
('СДЭК',         'Екатеринбург',    'ул. Вайнера, 13',             56.8350, 60.5990, 'Ежедневно 10:00–21:00', 'Пешеходная улица', true),
('Почта России', 'Екатеринбург',    'пр-т Ленина, 39',             56.8390, 60.6150, 'Пн–Пт 08:00–20:00',     'Главпочтамт', false);

-- Фото: приёмка на хабе (HUB) и вложения покупателя к спору (DISPUTE). До 5 МБ, хранятся в базе.
create table order_photos (
    id           bigserial primary key,
    order_id     bigint       not null references orders (id) on delete cascade,
    line_no      integer,
    kind         varchar(10)  not null,
    content_type varchar(40)  not null,
    size_bytes   integer      not null,
    data         bytea        not null,
    author       varchar(20)  not null,
    created_at   timestamptz  not null
);
create index order_photos_order_idx on order_photos (order_id);

-- Реестр выплат: одна выплата — один продавец; строки заказов помечаются номером выплаты, чтобы не заплатить дважды.
create table payouts (
    id                    bigserial primary key,
    number                varchar(24)  not null unique,
    seller_id             bigint       not null references sellers (id),
    gross_eur_cents       bigint       not null,
    commission_eur_cents  bigint       not null,
    net_eur_cents         bigint       not null,
    lines_count           integer      not null,
    status                varchar(10)  not null,
    bank_ref              varchar(60),
    created_at            timestamptz  not null,
    paid_at               timestamptz
);
create index payouts_seller_idx on payouts (seller_id);
create sequence payout_number_seq start 1;

alter table order_lines add column payout_id            bigint;
alter table order_lines add column commission_eur_cents bigint;

-- Уведомления: события заказа — исходящая очередь (outbox). Старые события не рассылаем.
alter table order_events add column notify      boolean not null default false;
alter table order_events add column notified_at timestamptz;
create index order_events_outbox_idx on order_events (id) where notify and notified_at is null;

create table notifications (
    id          bigserial primary key,
    order_id    bigint        references orders (id) on delete cascade,
    buyer_sub   varchar(64),
    channel     varchar(5)    not null,
    recipient   varchar(200)  not null,
    subject     varchar(200),
    body        text          not null,
    status      varchar(10)   not null,
    provider    varchar(20)   not null,
    error       varchar(500),
    created_at  timestamptz   not null
);
create index notifications_order_idx on notifications (order_id);
create index notifications_buyer_idx on notifications (buyer_sub);
