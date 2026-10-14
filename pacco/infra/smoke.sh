#!/usr/bin/env bash
# Проверка развёрнутого стека через Caddy: витрина, каталог, корзина, заказ, оплата, админка с токеном Keycloak.
# Использование: BASE=http://localhost ADMIN_PASSWORD=change-me-now infra/smoke.sh
set -Eeuo pipefail

BASE="${BASE:-http://localhost}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-change-me-now}"
TIMEOUT="${TIMEOUT:-420}"

step() { printf '\n== %s\n' "$*"; }
fail() { printf 'FAIL: %s\n' "$*" >&2; [[ -n "${GITHUB_ACTIONS:-}" ]] && echo "::error::smoke: $*"; exit 1; }
# Любая упавшая команда (например, curl -f с ответом 4xx) — аннотация с номером строки и командой.
trap 'rc=$?; [[ -n "${GITHUB_ACTIONS:-}" ]] && echo "::error::smoke: строка $LINENO, код $rc: $BASH_COMMAND"' ERR

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

point=$(curl -fsS "$BASE/api/pickup-points?city=%D0%9C%D0%BE%D1%81%D0%BA%D0%B2%D0%B0" | jq '.[0].id')
[[ "$point" =~ ^[0-9]+$ ]] || fail "нет пунктов выдачи в Москве"
# PNG 1×1 для фото с приёмки
printf '%s' 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==' | base64 -d > /tmp/ilpacco-photo.png

new_order() {
  local cart
  cart=$(curl -fsS -X POST "$BASE/api/carts" | jq -r .id)
  curl -fsS -X POST -H 'Content-Type: application/json' -d "{\"productId\":$loafers,\"size\":\"${2:-41}\"}" "$BASE/api/carts/$cart/items" >/dev/null
  curl -fsS -X POST -H 'Content-Type: application/json' -d "{\"productId\":$oil}" "$BASE/api/carts/$cart/items" >/dev/null
  curl -fsS -X POST -H 'Content-Type: application/json' -d "{\"cartId\":\"$cart\",\"mode\":\"GROUND\",\"buyerName\":\"$1\",\"phone\":\"+7 916 000-00-00\",\"passport\":\"4500123456\",\"inn\":\"770000000000\",\"pickupPointId\":$point,\"email\":\"buyer@example.ru\",\"consent\":true}" "$BASE/api/orders"
}
pay() { curl -fsS -X POST -H "X-Order-Key: $2" -H 'Content-Type: application/json' -d '{"method":"SBP"}' "$BASE/api/orders/$1/pay" | jq -r .status; }
api_post() {
  local body="${3:-}"
  [[ -n "$body" ]] || body='{}'
  curl -fsS -X POST -H "Authorization: Bearer $1" -H 'Content-Type: application/json' -d "$body" "$BASE$2"
}

step "Оформление и оплата"
order=$(new_order "Смоук Тест")
number=$(jq -r .number <<<"$order"); key=$(jq -r .accessKey <<<"$order")
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/orders/$number/pay")
[[ "$code" == "403" ]] || fail "оплата без ключа заказа: $code, ожидали 403"
status=$(pay "$number" "$key")
[[ "$status" == "PAID" ]] || fail "после оплаты статус $status"

step "Админка без токена закрыта"
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/admin/orders/$number/advance")
[[ "$code" == "401" ]] || fail "без токена ответ $code, ожидали 401"

token_for() {
  curl -fsS -d grant_type=password -d client_id=pacco-web -d "username=$1" -d "password=$ADMIN_PASSWORD" \
    "$BASE/auth/realms/pacco/protocol/openid-connect/token" | jq -r .access_token
}

seller=$(token_for seller); ops=$(token_for ops); support=$(token_for support); admin=$(token_for admin)
for t in "$seller" "$ops" "$support" "$admin"; do [[ -n "$t" && "$t" != "null" ]] || fail "Keycloak не выдал токен"; done

step "Продавец подтверждает и отправляет на хаб"
mine=$(curl -fsS -H "Authorization: Bearer $seller" "$BASE/api/seller/products" | jq length)
(( mine >= 2 )) || fail "у демо-продавца $mine товаров"
status=$(api_post "$seller" "/api/seller/orders/$number/confirm" | jq -r .status)
[[ "$status" == "CONFIRMED" ]] || fail "после подтверждения продавцом статус $status"
status=$(api_post "$seller" "/api/seller/orders/$number/ship" '{"tracking":"BRT-7781"}' | jq -r .status)
[[ "$status" == "TO_HUB" ]] || fail "после отправки на хаб статус $status"

step "Хаб: фото, приёмка, упаковка, передача перевозчику"
photo=$(curl -fsS -X POST -H "Authorization: Bearer $ops" -F "file=@/tmp/ilpacco-photo.png;type=image/png" "$BASE/api/ops/orders/$number/photos?lineNo=0" | jq .id)
code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/orders/$number/photos/$photo")
[[ "$code" == "403" ]] || fail "фото заказа доступно без ключа: $code"
curl -fsS -o /dev/null -H "X-Order-Key: $key" "$BASE/api/orders/$number/photos/$photo" || fail "покупатель не видит фото с хаба"
status=$(api_post "$ops" "/api/ops/orders/$number/accept" '{"lines":[{"lineNo":0,"ok":true},{"lineNo":1,"ok":true}]}' | jq -r .status)
[[ "$status" == "AT_HUB" ]] || fail "после приёмки статус $status"
api_post "$ops" "/api/ops/orders/$number/pack" '{"weightGrams":1350}' >/dev/null
status=$(api_post "$ops" "/api/ops/orders/$number/handover" '{"tracking":"CDEK-1000245"}' | jq -r .status)
[[ "$status" == "HANDED_TO_CARRIER" ]] || fail "после передачи перевозчику статус $status"

step "Таможня, ПВЗ, выдача по коду"
api_post "$ops" "/api/ops/orders/$number/customs" >/dev/null
api_post "$ops" "/api/ops/orders/$number/release" >/dev/null
api_post "$ops" "/api/ops/orders/$number/arrive" >/dev/null
[[ "$(curl -fsS "$BASE/api/orders/$number" | jq -r .pickupCode)" == "null" ]] || fail "код получения виден без ключа"
pickup=$(curl -fsS -H "X-Order-Key: $key" "$BASE/api/orders/$number" | jq -r .pickupCode)
[[ "$pickup" =~ ^[0-9]{6}$ ]] || fail "нет кода получения: $pickup"
status=$(api_post "$ops" "/api/ops/orders/$number/issue" "{\"code\":\"$pickup\"}" | jq -r .status)
[[ "$status" == "RECEIVED" ]] || fail "после выдачи статус $status"

step "Спор и возврат"
curl -fsS -X POST -H "X-Order-Key: $key" -H 'Content-Type: application/json' \
  -d '{"lines":[1],"reason":"DAMAGED","comment":"Бутылка протекла"}' "$BASE/api/orders/$number/disputes" >/dev/null
dispute=$(curl -fsS -H "Authorization: Bearer $support" "$BASE/api/admin/disputes?status=OPEN" | jq ".[] | select(.order.number == \"$number\") | .dispute.id")
refund=$(api_post "$support" "/api/admin/disputes/$dispute/resolve" '{"refund":true}' | jq .order.refundRub)
(( refund > 0 )) || fail "возврат по спору $refund"
status=$(api_post "$ops" "/api/ops/orders/$number/complete" | jq -r .status)
[[ "$status" == "COMPLETED" ]] || fail "после завершения статус $status"
events=$(curl -fsS "$BASE/api/orders/$number" | jq '.events | length')
(( events >= 12 )) || fail "в истории заказа $events событий"

step "Реестр выплат"
finance=$(token_for finance)
made=$(api_post "$finance" "/api/admin/payouts/batches" | jq '[.[] | select(.sellerName == "Calzoleria Verdi")] | length')
[[ "$made" == "1" ]] || fail "в реестре нет выплаты Calzoleria Verdi"

step "Уведомления: письмо в Mailpit и журнал СМС"
for i in $(seq 1 24); do
  mails=$(curl -fsS "http://127.0.0.1:8025/api/v1/messages?limit=200" | jq --arg n "$number" '[.messages[] | select(.Subject | contains($n))] | length')
  (( mails >= 4 )) && break
  sleep 5
done
(( mails >= 4 )) || fail "в Mailpit $mails писем по заказу $number, ожидали не меньше 4"
sms=$(curl -fsS -H "Authorization: Bearer $support" "$BASE/api/admin/notifications?order=$number" | jq --arg c "$pickup" '[.[] | select(.channel == "SMS" and (.body | contains("Код получения " + $c)))] | length')
(( sms >= 1 )) || fail "нет СМС с кодом получения в журнале"

step "Заказы в разных статусах для кабинетов"
o=$(new_order "Елена Орлова" 42); n2=$(jq -r .number <<<"$o"); pay "$n2" "$(jq -r .accessKey <<<"$o")" >/dev/null
o=$(new_order "Павел Кузнецов" 43); n3=$(jq -r .number <<<"$o"); pay "$n3" "$(jq -r .accessKey <<<"$o")" >/dev/null
api_post "$seller" "/api/seller/orders/$n3/confirm" >/dev/null
api_post "$seller" "/api/seller/orders/$n3/ship" '{"tracking":"BRT-7790"}' >/dev/null
o=$(new_order "Ольга Миронова" 40); n4=$(jq -r .number <<<"$o"); k4=$(jq -r .accessKey <<<"$o"); pay "$n4" "$k4" >/dev/null
api_post "$admin" "/api/admin/orders/$n4/advance" >/dev/null
api_post "$seller" "/api/seller/orders/$n4/ship" '{"tracking":"BRT-7799"}' >/dev/null
api_post "$ops" "/api/ops/orders/$n4/accept" '{"lines":[{"lineNo":0,"ok":true},{"lineNo":1,"ok":true}]}' >/dev/null
api_post "$ops" "/api/ops/orders/$n4/pack" '{"weightGrams":1290}' >/dev/null
api_post "$ops" "/api/ops/orders/$n4/handover" '{"tracking":"CDEK-1000301"}' >/dev/null
for s in customs release arrive; do api_post "$ops" "/api/ops/orders/$n4/$s" >/dev/null; done
printf '%s\n%s\n%s\n%s\n' "$n4" "$k4" "$n2" "$n3" > "${STATE_FILE:-/tmp/ilpacco-orders}"

step "Права"
code=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $seller" "$BASE/api/admin/orders")
[[ "$code" == "403" ]] || fail "продавец получил доступ к админке: $code"
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $support" "$BASE/api/ops/orders/$n3/accept")
[[ "$code" == "403" ]] || fail "поддержка получила доступ к хабу: $code"
code=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $(token_for finance)" "$BASE/api/admin/payouts")
[[ "$code" == "200" ]] || fail "финансы не видят реестр выплат: $code"

step "Страницы отдаются"
for path in /admin /seller /sell /legal /account "/orders/$number" "/pay/$n4" /silent-check-sso.html; do
  curl -fsS -o /dev/null "$BASE$path" || fail "$path недоступна"
done
login_page=$(curl -sS "$BASE/auth/realms/pacco/protocol/openid-connect/auth?client_id=pacco-web&response_type=code&scope=openid&redirect_uri=$BASE/admin&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256")
if ! grep -q "ilpacco.css" <<<"$login_page"; then
  css=$(grep -o 'href="[^"]*\.css"' <<<"$login_page" | tr '\n' ' ')
  fail "страница входа без темы ilpacco; стили: ${css:-нет} ; заголовок: $(grep -o '<title>[^<]*' <<<"$login_page")"
fi

printf '\nOK: заказ %s прошёл весь путь до выдачи, спора и завершения; для кабинетов созданы %s, %s, %s\n' "$number" "$n2" "$n3" "$n4"
