// Браузерная проверка кабинетов: вход через Keycloak, админка и кабинет продавца.
// Запуск: BASE=http://localhost PASSWORD=change-me-now node infra/e2e/cabinets.mjs
import { chromium } from "playwright";

const BASE = process.env.BASE ?? "http://localhost";
const PASSWORD = process.env.PASSWORD ?? "change-me-now";
const OUT = process.env.OUT ?? "e2e-screens";
const MAILPIT = process.env.MAILPIT ?? "http://127.0.0.1:8025";
// Заказы в разных статусах, которые подготовил infra/smoke.sh: [в ПВЗ, ключ, ждёт продавца, едет на хаб]
const [atPickup, , paid, toHub] = (await import("node:fs")).readFileSync(process.env.ORDERS_FILE ?? "/tmp/ilpacco-orders", "utf8").trim().split("\n");

async function login(page, user) {
  await page.waitForSelector("#username", { timeout: 60_000 });
  await page.fill("#username", user);
  await page.fill("#password", PASSWORD);
  await Promise.all([page.waitForURL(`${BASE}/**`, { timeout: 60_000 }), page.click("#kc-login")]);
}

async function expectText(page, text, what) {
  try {
    await page.getByText(text, { exact: false }).first().waitFor({ timeout: 30_000 });
    console.log(`  ✓ ${what}`);
  } catch (e) {
    await page.screenshot({ path: `${OUT}/failed-${what.replace(/\s+/g, "-")}.png`, fullPage: true });
    throw new Error(`Не найдено «${text}» (${what})`);
  }
}

const browser = await chromium.launch();
try {
  console.log("Админка (admin)");
  const admin = await (await browser.newContext({ locale: "ru-RU" })).newPage();
  await admin.goto(`${BASE}/admin`);
  await admin.waitForSelector("#username", { timeout: 60_000 });
  await admin.screenshot({ path: `${OUT}/login.png` });
  await login(admin, "admin");
  await expectText(admin, "Заявки продавцов", "обзор с плитками");
  await admin.screenshot({ path: `${OUT}/admin-overview.png`, fullPage: true });
  await admin.goto(`${BASE}/admin/products`);
  await expectText(admin, "Весь каталог", "каталог в админке");
  await expectText(admin, "Лоферы из замши", "товары в админке");
  await admin.goto(`${BASE}/admin/orders`);
  await expectText(admin, "PC-", "список заказов");
  await admin.screenshot({ path: `${OUT}/admin-orders.png`, fullPage: true });
  await admin.goto(`${BASE}/admin/compliance`);
  await expectText(admin, "Сохранить и перепроверить каталог", "форма правил");

  console.log("Операции: хаб, доставка, карточка заказа, споры, выплаты");
  await admin.goto(`${BASE}/admin/hub`);
  await expectText(admin, toHub, "заказ в очереди приёмки");
  await admin.screenshot({ path: `${OUT}/ops-hub.png`, fullPage: true });
  const card = admin.locator(".qcard", { hasText: toHub });
  // фото с приёмки: сгенерированная картинка «товара» 
  const png = await admin.evaluate(() => {
    const c = document.createElement("canvas");
    c.width = 480; c.height = 360;
    const g = c.getContext("2d");
    g.fillStyle = "#e6ebf2"; g.fillRect(0, 0, 480, 360);
    g.fillStyle = "#22355a"; g.beginPath(); g.ellipse(240, 200, 170, 70, 0, 0, Math.PI * 2); g.fill();
    g.fillStyle = "#16243f"; g.fillRect(70, 250, 340, 22);
    return c.toDataURL("image/png").split(",")[1];
  });
  await card.locator("input[type=file]").first().setInputFiles({ name: "priemka.png", mimeType: "image/png", buffer: Buffer.from(png, "base64") });
  await card.getByRole("button", { name: "Принять на хабе" }).click();
  await expectText(admin, `${toHub}: приёмка завершена, фото: 1`, "приёмка на хабе с фото");
  await admin.getByRole("tab", { name: /Упаковка/ }).click();
  await admin.locator(".qcard", { hasText: toHub }).getByRole("button", { name: "Упаковано" }).click();
  await expectText(admin, `${toHub}: упакован`, "упаковка");
  await admin.goto(`${BASE}/admin/delivery`);
  await admin.getByRole("tab", { name: /В пунктах выдачи/ }).click();
  await expectText(admin, atPickup, "заказ в ПВЗ");
  await admin.screenshot({ path: `${OUT}/ops-delivery.png`, fullPage: true });
  await admin.goto(`${BASE}/admin/orders/${atPickup}`);
  await expectText(admin, "История", "карточка заказа");
  await expectText(admin, "Передан перевозчику", "история событий");
  await admin.screenshot({ path: `${OUT}/ops-order.png`, fullPage: true });
  await admin.goto(`${BASE}/admin/disputes`);
  await admin.getByRole("tab", { name: /С возвратом/ }).click();
  await expectText(admin, "Повреждён", "решённый спор");
  await admin.screenshot({ path: `${OUT}/ops-disputes.png`, fullPage: true });
  await admin.goto(`${BASE}/admin/orders/${toHub}`);
  await admin.locator(".photo img").first().waitFor({ timeout: 30_000 });
  console.log("  ✓ фото с приёмки в карточке заказа");
  await admin.goto(`${BASE}/admin/payouts`);
  await expectText(admin, "Calzoleria Verdi", "реестр выплат");
  await admin.getByLabel(/Номер платёжки/).first().fill("SWIFT 2026-10-14/001");
  await admin.getByRole("button", { name: "Выплачено" }).first().click();
  await expectText(admin, "выплата отмечена", "отметка «выплачено»");
  await admin.screenshot({ path: `${OUT}/ops-payouts.png`, fullPage: true });
  await admin.getByRole("link", { name: "Документы" }).first().click();
  await expectText(admin, "К перечислению", "отчёт агента");
  await admin.screenshot({ path: `${OUT}/ops-payout-report.png`, fullPage: true });
  await admin.goto(`${BASE}/admin/notifications`);
  await expectText(admin, "Код получения", "журнал уведомлений");
  await admin.screenshot({ path: `${OUT}/ops-notifications.png`, fullPage: true });
  await admin.goto(`${BASE}/admin`);
  await expectText(admin, "Хаб: упаковка и передача", "обзор по этапам");
  await admin.screenshot({ path: `${OUT}/admin-overview.png`, fullPage: true });

  console.log("Кабинет продавца (seller)");
  const seller = await (await browser.newContext({ locale: "it-IT" })).newPage();
  await seller.goto(`${BASE}/seller`);
  await login(seller, "seller");
  await expectText(seller, "I tuoi prodotti", "список товаров продавца");
  await expectText(seller, "Лоферы из замши", "товар продавца");
  await seller.fill("#hs", "64039113");
  await seller.fill("#price", "340");
  await expectText(seller, "C-01", "проверка комплаенса на лету");
  await seller.screenshot({ path: `${OUT}/seller-products.png`, fullPage: true });
  await seller.goto(`${BASE}/seller/orders`);
  await expectText(seller, paid, "заказ ждёт подтверждения");
  await seller.screenshot({ path: `${OUT}/seller-orders.png`, fullPage: true });
  await seller.locator(".qcard", { hasText: paid }).getByRole("button", { name: "Conferma l'ordine" }).click();
  await expectText(seller, `Ordine ${paid} confermato`, "подтверждение продавцом");
  await seller.getByRole("tab", { name: /Da spedire/ }).click();
  await seller.locator(".qcard", { hasText: paid }).getByLabel("Tracking del corriere").fill("BRT-55120");
  await seller.locator(".qcard", { hasText: paid }).getByRole("button", { name: "Segna come spedito" }).click();
  await expectText(seller, `Ordine ${paid}: spedizione registrata`, "отправка на хаб");
  await seller.goto(`${BASE}/seller/orders/${paid}`);
  await expectText(seller, "Hub Milano", "лист для посылки");
  await seller.screenshot({ path: `${OUT}/seller-slip.png`, fullPage: true });
  await seller.goto(`${BASE}/seller/payouts`);
  await expectText(seller, "In arrivo", "выплаты продавца");
  await expectText(seller, "Pagato", "история выплат");
  await seller.getByRole("link", { name: "Rendiconto e fattura" }).first().click();
  await expectText(seller, "Da bonificare", "отчёт для продавца");
  await seller.getByRole("radio", { name: /Fattura/ }).click();
  await expectText(seller, "inversione contabile", "счёт на комиссию");
  await seller.screenshot({ path: `${OUT}/seller-invoice.png`, fullPage: true });

  await seller.goto(`${BASE}/sell`);
  await expectText(seller, "Come funziona", "страница для продавцов");
  await seller.screenshot({ path: `${OUT}/sell-landing.png`, fullPage: true });

  console.log("Письмо покупателю в Mailpit");
  const box = await (await fetch(`${MAILPIT}/api/v1/messages?limit=100`)).json();
  const mail = box.messages.find((m) => m.Subject.includes(atPickup) && m.Subject.includes("можно забирать"));
  if (!mail) throw new Error("Нет письма «можно забирать» по заказу " + atPickup);
  const mailPage = await (await browser.newContext({ viewport: { width: 640, height: 900 } })).newPage();
  await mailPage.goto(`${MAILPIT}/view/${mail.ID}.html`);
  await expectText(mailPage, "Код получения", "письмо с кодом");
  await mailPage.screenshot({ path: `${OUT}/mail-pickup.png`, fullPage: true });

  console.log("Чужой пользователь не попадает в админку");
  await seller.goto(`${BASE}/admin`);
  await expectText(seller, "нет прав сотрудника", "закрытая админка для продавца");
  console.log("OK: кабинеты работают в браузере");
} finally {
  await browser.close();
}
