#!/usr/bin/env bash
# Поднимает ilpacco на этом компьютере: http://127.0.0.1:8025
# Нужен Docker Desktop (или Docker Engine) с Compose v2. Первая сборка — 5–10 минут.
#   ./start-local.sh          — собрать из исходников и запустить
#   ./start-local.sh --pull   — взять готовые образы из GHCR (быстрее, если пакеты открыты)
#   ./start-local.sh --stop   — остановить (данные сохраняются)
set -euo pipefail
cd "$(dirname "$0")"
compose() { docker compose --env-file local.env "$@"; }

case "${1:-}" in
  --stop) compose down; exit 0 ;;
  --pull) compose pull backend web && compose up -d --no-build ;;
  *) compose up -d --build ;;
esac

echo "Ждём, пока поднимутся сервисы (Keycloak стартует дольше всех)…"
for i in $(seq 1 90); do
  if curl -fsS -o /dev/null http://127.0.0.1:8025/api/products && \
     curl -fsS -o /dev/null http://127.0.0.1:8025/auth/realms/pacco/.well-known/openid-configuration; then
    cat <<MSG

ilpacco запущен:
  Витрина          http://127.0.0.1:8025
  Кабинет покупателя http://127.0.0.1:8025/account   (регистрация на странице входа)
  Операции         http://127.0.0.1:8025/admin     admin / change-me-now (ещё ops, support, finance, moderator)
  Кабинет продавца http://127.0.0.1:8025/seller    seller / change-me-now
  Письма (Mailpit) http://127.0.0.1:8026
  Keycloak         http://127.0.0.1:8025/auth      admin / change-me-now

Проверить весь путь заказа:  BASE=http://127.0.0.1:8025 MAILPIT=http://127.0.0.1:8026 ./infra/smoke.sh
Остановить:                  ./start-local.sh --stop
MSG
    exit 0
  fi
  sleep 5
done
echo "Сервисы не ответили за 7,5 минут. Логи: docker compose --env-file local.env logs --tail 100" >&2
exit 1
