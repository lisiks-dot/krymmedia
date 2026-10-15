#!/usr/bin/env bash
# Установка ilpacco на VPS (Ubuntu/Debian) с root-доступом по SSH. Запуск на сервере:
#   curl -fsSL https://raw.githubusercontent.com/lisiks-dot/krymmedia/main/pacco/deploy-server.sh | sudo bash
# Режимы:
#   (по умолчанию) за панелью хостинга: ilpacco на 127.0.0.1:8080, домен и HTTPS — в панели (FASTPANEL, ISPmanager)
#   MODE=standalone — без панели: Caddy сам занимает 80/443 и получает сертификат Let's Encrypt
set -euo pipefail
DOMAIN="${DOMAIN:-ilpacco.ru}"
MODE="${MODE:-panel}"
DIR="${DIR:-/opt/ilpacco}"
REF="${REF:-main}"

[[ $EUID -eq 0 ]] || { echo "Запустите от root (sudo)"; exit 1; }
command -v docker >/dev/null || { echo "Ставим Docker…"; curl -fsSL https://get.docker.com | sh; }
docker compose version >/dev/null

mkdir -p "$DIR" && cd "$DIR"
echo "Скачиваем ilpacco ($REF)…"
curl -fsSL "https://codeload.github.com/lisiks-dot/krymmedia/tar.gz/$REF" | tar -xz --strip-components=1 --wildcards '*/pacco/*'
cd pacco

if [[ ! -f server.env ]]; then
  gen() { tr -dc 'A-Za-z0-9' </dev/urandom | head -c 24; }
  sed -e "s|^PUBLIC_URL=.*|PUBLIC_URL=https://$DOMAIN|" \
      -e "s|^DB_PASSWORD=.*|DB_PASSWORD=$(gen)|" \
      -e "s|^KEYCLOAK_ADMIN_PASSWORD=.*|KEYCLOAK_ADMIN_PASSWORD=$(gen)|" server.env.example > server.env
  if [[ "$MODE" == "standalone" ]]; then
    sed -i -e "s|^SITE_ADDRESS=.*|SITE_ADDRESS=$DOMAIN|" -e "s|^HTTP_BIND=.*|HTTP_BIND=0.0.0.0|" \
           -e "s|^HTTP_PORT=.*|HTTP_PORT=80|" -e "s|^HTTPS_PORT=.*|HTTPS_PORT=443|" server.env
  fi
  chmod 600 server.env
  echo "Создан server.env с новыми паролями (только на этом сервере)."
fi

echo "Собираем и запускаем (первый раз 5–10 минут)…"
docker compose --env-file server.env up -d --build

PORT=$(grep ^HTTP_PORT server.env | cut -d= -f2)
for i in $(seq 1 90); do
  if curl -fsS -o /dev/null "http://127.0.0.1:$PORT/api/products" -H "Host: $DOMAIN"; then break; fi
  sleep 5
done
curl -fsS -o /dev/null "http://127.0.0.1:$PORT/api/products" -H "Host: $DOMAIN" || { echo "Сервисы не ответили. Логи: docker compose --env-file server.env logs --tail 100"; exit 1; }

echo
echo "ilpacco работает на сервере: http://127.0.0.1:$PORT"
if [[ "$MODE" == "panel" ]]; then
  cat <<MSG
Осталось в панели хостинга для сайта $DOMAIN:
  1. Включить SSL-сертификат Let's Encrypt.
  2. Настроить проксирование всех запросов на http://127.0.0.1:$PORT
     (FASTPANEL: Сайт → Настройки → «Проксирование» / Node.js-обработчик по адресу; ISPmanager: «Обработчик — Proxy»).
     Готовый блок для nginx — в pacco/infra/nginx-panel.conf.
После этого: https://$DOMAIN · кабинет сотрудников https://$DOMAIN/admin
Пароли admin/seller и др. — change-me-now: смените сразу в https://$DOMAIN/auth/admin (realm pacco).
MSG
else
  echo "Сайт: https://$DOMAIN (сертификат Caddy получит сам, если A-запись указывает на этот сервер)"
fi
