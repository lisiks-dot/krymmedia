create table sellers (
    id            bigserial primary key,
    keycloak_sub  varchar(64)  not null unique,
    legal_name    varchar(200) not null,
    partita_iva   varchar(20)  not null unique,
    iban          varchar(40)  not null,
    region        varchar(100),
    contact_email varchar(200) not null,
    status        varchar(20)  not null,
    reject_reason varchar(500),
    created_at    timestamptz  not null
);

alter table products add column seller_id bigint references sellers (id);
create index products_seller_idx on products (seller_id);

alter table order_lines add column seller_id bigint;
alter table order_lines add column confirmed boolean not null default false;
create index order_lines_seller_idx on order_lines (seller_id);

-- Демо-продавец: пользователь Keycloak «seller» с фиксированным id (см. infra/keycloak/realm-pacco.json).
insert into sellers (keycloak_sub, legal_name, partita_iva, iban, region, contact_email, status, created_at)
values ('11111111-1111-4111-8111-111111111111', 'Calzoleria Verdi', 'IT01234560428', 'IT60X0542811101000000123456',
        'Марке', 'info@calzoleriaverdi.example', 'ACTIVE', now());

update products set seller_id = (select id from sellers where partita_iva = 'IT01234560428')
where seller_name = 'Calzoleria Verdi';
