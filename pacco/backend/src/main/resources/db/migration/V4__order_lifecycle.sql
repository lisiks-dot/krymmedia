-- Жизненный цикл заказа от оплаты до выдачи: история событий, состояние каждой строки,
-- ключ покупателя, возвраты, хаб (вес, трек перевозчика), ПВЗ (код получения, срок хранения), споры.

alter table orders add column access_key uuid;
update orders set access_key = gen_random_uuid() where access_key is null;
alter table orders alter column access_key set not null;

alter table orders add column payment_method   varchar(10);
alter table orders add column refund_rub       bigint not null default 0;
alter table orders add column cancel_reason    varchar(300);
alter table orders add column parcel_weight_g  integer;
alter table orders add column carrier_tracking varchar(60);
alter table orders add column pickup_code      varchar(6);
alter table orders add column storage_until    date;
alter table orders add column received_at      timestamptz;
create index orders_status_idx on orders (status);

-- Порядковый номер строки — стабильный идентификатор для действий продавца, хаба и спора.
alter table order_lines add column line_no integer;
update order_lines ol set line_no = x.rn - 1
from (select ctid, row_number() over (partition by order_id) as rn from order_lines) x
where ol.ctid = x.ctid;
alter table order_lines alter column line_no set not null;

alter table order_lines add column state varchar(20) not null default 'PENDING';
update order_lines set state = case when seller_id is null then 'SHIPPED' when confirmed then 'CONFIRMED' else 'PENDING' end;
alter table order_lines add column seller_tracking varchar(60);
alter table order_lines add column note            varchar(300);

create table order_events (
    id       bigserial primary key,
    order_id bigint       not null references orders (id) on delete cascade,
    status   varchar(30)  not null,
    title    varchar(200) not null,
    note     varchar(500),
    actor    varchar(20)  not null,
    at       timestamptz  not null
);
create index order_events_order_idx on order_events (order_id, at);

insert into order_events (order_id, status, title, actor, at)
select id, status, 'Статус на момент обновления системы', 'system', updated_at from orders;

create table disputes (
    id            bigserial primary key,
    order_id      bigint        not null references orders (id) on delete cascade,
    line_nos      varchar(100)  not null,
    reason        varchar(30)   not null,
    comment       varchar(1000),
    status        varchar(20)   not null,
    refund_rub    bigint        not null default 0,
    resolution    varchar(500),
    created_at    timestamptz   not null,
    resolved_at   timestamptz
);
create index disputes_order_idx on disputes (order_id);
create index disputes_status_idx on disputes (status);
