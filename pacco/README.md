# ilpacco.ru — маркетплейс Италия → Россия

Итальянские продавцы, покупатели в России, доставка посылками через хаб в Милане.
Здесь рабочее ядро MVP: каталог, комплаенс-движок, расчёт цены «под ключ», корзина, заказы со статусами,
витрина, кабинет продавца и админка. Бизнес-логика соответствует бизнес-плану и ТЗ (модель v2: одна посылка на заказ,
наземная доставка по умолчанию, тариф «себестоимость + 25 %»).

Публичный адрес — **ilpacco.ru**. Внутренние идентификаторы (папка `pacco/`, realm Keycloak `pacco`,
базы и Docker-тома, Java-пакеты `ru.pacco`) оставлены прежними, чтобы не терять данные уже развёрнутых серверов.

## Что внутри

| Часть | Технологии | Папка |
| --- | --- | --- |
| Бэкенд | Java 25, Spring Boot 4.1, Spring Modulith 2.1, JPA + Flyway, Spring Security (JWT) | `backend/` |
| Витрина | Next.js 16, React 19, TypeScript; каталог и карточка рендерятся на сервере | `web/` |
| Данные | PostgreSQL 17 (каталог, заказы, правила), Redis 7 (корзины) | `docker-compose.yml` |
| Вход в админку | Keycloak 26, realm `pacco`, роли admin / moderator / support / finance | `infra/keycloak/` |
| Прокси и HTTPS | Caddy 2 — сертификат Let's Encrypt получает сам | `infra/Caddyfile` |
| CI/CD | GitHub Actions: тесты, сборка, запуск стека и сквозная проверка, образы в GHCR, деплой по SSH | `../.github/workflows/` |

### Модули бэкенда

Каждый пакет первого уровня — модуль Spring Modulith. Модули общаются только через публичные типы корневого
пакета; тест `ModularityTest` это проверяет и рисует схему модулей (PlantUML в отчётах CI).

| Модуль | Что делает | API |
| --- | --- | --- |
| `compliance` | Правила C-01…C-04: порог 290 € за единицу (санкции ЕС), продэмбарго РФ, алкоголь, аэрозоли. Хранятся в БД, меняются без релиза | `POST /api/compliance/check`, `GET/PUT /api/admin/compliance/rules` |
| `catalog` | Товары; при смене правил весь каталог перепроверяется: нарушители снимаются, подходящие возвращаются | `GET /api/products`, `GET /api/products/{id}`, `GET/POST /api/admin/products` |
| `pricing` | Цена «под ключ»: курс + буфер, доставка (наземная/авиа), пошлина ЕАЭС 5 % свыше 200 €, сценарий НДС 22 %, подсказка «какой товар убрать» | `GET /api/pricing/tariffs` |
| `cart` | Корзины в Redis, 30 дней | `POST /api/carts`, `GET /api/carts/{id}`, `POST/PUT /api/carts/{id}/items` |
| `sellers` | Заявки продавцов (Partita IVA, IBAN), одобрение модератором; продавец привязан к пользователю Keycloak по `sub` | `GET/POST /api/seller/me`, `GET /api/admin/sellers`, `POST /api/admin/sellers/{id}/approve\|reject` |
| `orders` | Оформление, 12 статусов, заглушка эквайринга, запрет заказов из одной гастрономии; продавец подтверждает свои строки заказа, выплаты считаются по комиссии категории | `POST /api/orders`, `GET /api/orders/{n}`, `POST /api/orders/{n}/pay`, `GET /api/admin/orders`, `POST /api/admin/orders/{n}/advance`, `GET /api/seller/orders`, `POST /api/seller/orders/{n}/confirm`, `GET /api/seller/payouts` |

Карточки продавца проходят комплаенс при сохранении и попадают на модерацию (`GET /api/admin/products/pending`,
`POST /api/admin/products/{id}/approve|reject`). Документация API: `/swagger-ui.html` (через Caddy).

### Кабинеты

| Адрес | Для кого | Что внутри |
| --- | --- | --- |
| `/admin` | Сотрудники ilpacco (роли admin, moderator, support) | Обзор, заявки продавцов, модерация карточек, правила комплаенса с перепроверкой каталога, заказы |
| `/seller` | Продавцы, на итальянском | Заявка, товары с проверкой комплаенса на лету, смена цены, заказы на подтверждение, выплаты |

Вход через Keycloak (клиент `pacco-web`, PKCE). Продавец регистрируется сам по ссылке на странице входа,
подаёт заявку, после одобрения модератором получает доступ.

Демо-пользователи из импорта realm, пароль у всех `change-me-now`:
`admin` (администратор), `moderator`, `support`, `seller` (продавец Calzoleria Verdi).

## Локальный запуск

Нужен Docker с Compose v2.

```bash
cd pacco
cp .env.example .env
# в .env: PUBLIC_URL=http://localhost и SITE_ADDRESS=:80
docker compose up -d --build
./infra/smoke.sh          # сквозная проверка: каталог → корзина → заказ → оплата → продавец → админка
```

Сайт: http://localhost · Keycloak: http://localhost/auth (админ консоли — `admin` / `KEYCLOAK_ADMIN_PASSWORD`).

Разработка по частям:

```bash
docker compose up -d postgres redis keycloak
cd backend && mvn spring-boot:run          # http://localhost:8080
cd web && npm install && npm run dev       # http://localhost:3000, /api проксируется на 8080
cd backend && mvn verify                   # тесты; интеграционные требуют postgres и redis
```

## Деплой на сервер

Подойдёт VPS с Ubuntu 24.04, 2 vCPU и 4 ГБ RAM, например в Yandex Cloud или Selectel.

1. Установите Docker: `curl -fsSL https://get.docker.com | sh`.
2. Направьте A-запись домена на IP сервера и откройте порты 80 и 443.
3. Подготовьте каталог и настройки:
   ```bash
   sudo mkdir -p /opt/pacco && sudo chown $USER /opt/pacco && cd /opt/pacco
   git clone https://github.com/lisiks-dot/krymmedia.git src && cp -r src/pacco/* src/pacco/.env.example .
   cp .env.example .env && nano .env     # PUBLIC_URL, SITE_ADDRESS, пароли
   ```
4. Запустите — из готовых образов CI или со сборкой на сервере:
   ```bash
   docker compose pull && docker compose up -d     # образы ghcr.io/lisiks-dot/pacco-*
   # или
   docker compose up -d --build
   ```
5. Проверьте: `BASE=https://ваш-домен ./infra/smoke.sh`.

Дальше обновления выкатывает workflow **«ilpacco · деплой на сервер»** (Actions → Run workflow).
Ему нужны секреты репозитория `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_PUBLIC_URL`
и, если пакеты GHCR приватные, `GHCR_TOKEN` с правом `read:packages`.

## Перед запуском с реальными покупателями

- Сменить пароли пользователей `admin` и `support` в Keycloak (в импорте realm стоит `change-me-now`) и закрыть `/auth/admin` по IP.
- Подключить эквайринг МИР и СБП вместо `StubPaymentGateway` и платёжного агента для выплат продавцам.
- Хранить паспорт и ИНН зашифрованными у таможенного представителя (сейчас сохраняется только маска паспорта).
- Обновлять курс ЦБ задачей по расписанию (сейчас — переменная `RUB_PER_EUR`).
- Подключить OpenSearch для поиска с морфологией и S3 для фото товаров, когда каталог вырастет.
- Терминал хаба (приёмка, сверка с фото, консолидация) и выплаты через платёжного агента — следующие этапы по ТЗ.
- Realm Keycloak импортируется только при первом запуске. На уже развёрнутом сервере клиент `pacco-web` и пользователей нужно добавить в консоли Keycloak вручную.
