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
curl -fsS "$BASE/" | grep -q "Италия в одной посылке" || fail "главная страница без героя"
curl -fsS "$BASE/" | grep -q "Лоферы из замши" || fail "главная страница без товаров"

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

step "Админка с токеном Keycloak"
token=$(curl -fsS -d grant_type=password -d client_id=pacco-admin -d username=admin -d "password=$ADMIN_PASSWORD" \
  "$BASE/auth/realms/pacco/protocol/openid-connect/token" | jq -r .access_token)
[[ -n "$token" && "$token" != "null" ]] || fail "Keycloak не выдал токен"
status=$(curl -fsS -X POST -H "Authorization: Bearer $token" "$BASE/api/admin/orders/$number/advance" | jq -r .status)
[[ "$status" == "CONFIRMED" ]] || fail "после продвижения статус $status"

step "Трекинг на витрине"
curl -fsS -o /dev/null "$BASE/orders/$number" || fail "страница заказа недоступна"

printf '\nOK: заказ %s прошёл путь от каталога до подтверждения продавцом\n' "$number"
