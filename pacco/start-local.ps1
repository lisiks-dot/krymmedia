# Поднимает ilpacco на этом компьютере (Windows, Docker Desktop): http://127.0.0.1:8025
#   .\start-local.ps1          — собрать из исходников и запустить
#   .\start-local.ps1 -Pull    — взять готовые образы из GHCR
#   .\start-local.ps1 -Stop    — остановить (данные сохраняются)
param([switch]$Pull, [switch]$Stop)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
function Compose { docker compose --env-file local.env @args; if ($LASTEXITCODE) { throw "docker compose: код $LASTEXITCODE" } }

if ($Stop) { Compose down; exit 0 }
if ($Pull) { Compose pull backend web; Compose up -d --no-build } else { Compose up -d --build }

Write-Host "Ждём, пока поднимутся сервисы (Keycloak стартует дольше всех)…"
for ($i = 0; $i -lt 90; $i++) {
  try {
    Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8025/api/products | Out-Null
    Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8025/auth/realms/pacco/.well-known/openid-configuration | Out-Null
    Write-Host ""
    Write-Host "ilpacco запущен:"
    Write-Host "  Витрина            http://127.0.0.1:8025"
    Write-Host "  Кабинет покупателя http://127.0.0.1:8025/account"
    Write-Host "  Операции           http://127.0.0.1:8025/admin    admin / change-me-now"
    Write-Host "  Кабинет продавца   http://127.0.0.1:8025/seller   seller / change-me-now"
    Write-Host "  Письма (Mailpit)   http://127.0.0.1:8026"
    Start-Process "http://127.0.0.1:8025"
    exit 0
  } catch { Start-Sleep -Seconds 5 }
}
Write-Error "Сервисы не ответили за 7,5 минут. Логи: docker compose --env-file local.env logs --tail 100"
