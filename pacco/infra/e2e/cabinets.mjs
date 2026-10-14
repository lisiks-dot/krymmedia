// Браузерная проверка кабинетов: вход через Keycloak, админка и кабинет продавца.
// Запуск: BASE=http://localhost PASSWORD=change-me-now node infra/e2e/cabinets.mjs
import { chromium } from "playwright";

const BASE = process.env.BASE ?? "http://localhost";
const PASSWORD = process.env.PASSWORD ?? "change-me-now";
const OUT = process.env.OUT ?? "e2e-screens";

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
  await expectText(seller, "PC-", "заказы продавца");
  await seller.goto(`${BASE}/seller/payouts`);
  await expectText(seller, "In arrivo", "выплаты продавца");

  console.log("Чужой пользователь не попадает в админку");
  await seller.goto(`${BASE}/admin`);
  await expectText(seller, "нет прав сотрудника", "закрытая админка для продавца");
  console.log("OK: кабинеты работают в браузере");
} finally {
  await browser.close();
}
