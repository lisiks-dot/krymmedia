#!/usr/bin/env bash
# Проверка развёрнутого стека через Caddy: витрина, каталог, корзина, заказ, оплата, админка с токеном Keycloak.
# Использование: BASE=http://localhost ADMIN_PASSWORD=change-me-now infra/smoke.sh
set -euo pipefail

BASE="${BASE:-http://localhost}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-change-me-now}"
TIMEOUT="${TIMEOUT:-420}"

step() { printf '\n== %s\n' "$*"; }
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }

wait_for() {
  local url="$1" deadline=$((SECONDS + TIMEOUT))
  until curl -fsS -o /dev/null "$url"; do
    (( SECONDS < deadline )) || fail "не дождались $url"
    sleep 5
  done
}

step "Ждём бэкенд и Keycloak"
wait_for "$BASE/api/products"
wait_for "$BASE/auth/realms/pacco/.well-known/openid-configuration"

step "Витрина отдаёт каталог с сервера"
curl -fsS "$BASE/" | grep -q "Обувь и трикотаж от семейных мастерских Италии" || fail "главная страница без кампании"
curl -fsS "$BASE/catalog" | grep -q "Лоферы из замши" || fail "каталог без товаров"

step "Каталог"
products=$(curl -fsS "$BASE/api/products")
count=$(jq length <<<"$products")
(( count >= 13 )) || fail "в каталоге $count товаров"
loafers=$(jq '.[] | select(.title == "Лоферы из замши, мужские") | .id' <<<"$products")
oil=$(jq '.[] | select(.title == "Масло Extra Vergine, 0,75 л") | .id' <<<"$products")
curl -fsS "$BASE/p/$loafers" | grep -q "Семейная мастерская в Фермо" || fail "карточка товара не отрисовалась на сервере"

step "Корзина и цена «под ключ»"
cart=$(curl -fsS -X POST "$BASE/api/carts" | jq -r .id)
curl -fsS -X POST -H 'Content-Type: application/json' -d "{\"productId\":$loafers,\"size\":\"41\"}" "$BASE/api/carts/$cart/items" >/dev/null
total=$(curl -fsS -X POST -H 'Content-Type: application/json' -d "{\"productId\":$oil}" "$BASE/api/carts/$cart/items" | jq .quote.totalRub)
[[ "$total" == "23140" ]] || fail "итог корзины $total, ожидали 23140"

step "Оформление и оплата"
order=$(curl -fsS -X POST -H 'Content-Type: application/json' -d "{\"cartId\":\"$cart\",\"mode\":\"GROUND\",\"buyerName\":\"Смоук Тест\",\"phone\":\"+7 916 000-00-00\",\"passport\":\"4500123456\",\"inn\":\"770000000000\",\"pickupPoint\":\"СДЭК · Москва\",\"consent\":true}" "$BASE/api/orders")
number=$(jq -r .number <<<"$order")
status=$(curl -fsS -X POST "$BASE/api/orders/$number/pay" | jq -r .status)
[[ "$status" == "PAID" ]] || fail "после оплаты статус $status"

step "Админка без токена закрыта"
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/admin/orders/$number/advance")
[[ "$code" == "401" ]] || fail "без токена ответ $code, ожидали 401"

token_for() {
  curl -fsS -d grant_type=password -d client_id=pacco-web -d "username=$1" -d "password=$ADMIN_PASSWORD" \
    "$BASE/auth/realms/pacco/protocol/openid-connect/token" | jq -r .access_token
}

step "Кабинет продавца: товары и подтверждение заказа"
seller=$(token_for seller)
[[ -n "$seller" && "$seller" != "null" ]] || fail "Keycloak не выдал токен продавцу"
mine=$(curl -fsS -H "Authorization: Bearer $seller" "$BASE/api/seller/products" | jq length)
(( mine >= 2 )) || fail "у демо-продавца $mine товаров"
status=$(curl -fsS -X POST -H "Authorization: Bearer $seller" "$BASE/api/seller/orders/$number/confirm" | jq -r .status)
[[ "$status" == "CONFIRMED" ]] || fail "после подтверждения продавцом статус $status"

step "Админка с токеном Keycloak"
token=$(token_for admin)
[[ -n "$token" && "$token" != "null" ]] || fail "Keycloak не выдал токен"
status=$(curl -fsS -X POST -H "Authorization: Bearer $token" "$BASE/api/admin/orders/$number/advance" | jq -r .status)
[[ "$status" == "TO_HUB" ]] || fail "после продвижения статус $status"
code=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $seller" "$BASE/api/admin/orders")
[[ "$code" == "403" ]] || fail "продавец получил доступ к админке: $code"

step "Страницы кабинетов отдаются"
curl -fsS -o /dev/null "$BASE/admin" || fail "/admin недоступна"
curl -fsS -o /dev/null "$BASE/seller" || fail "/seller недоступна"

step "Трекинг на витрине"
curl -fsS -o /dev/null "$BASE/orders/$number" || fail "страница заказа недоступна"

printf '\nOK: заказ %s прошёл путь от каталога до подтверждения продавцом и передачи на хаб\n' "$number"
