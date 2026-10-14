// Браузерная проверка витрины: главная, каталог, карточка, избранное, корзина — на компьютере и телефоне.
// Запуск: BASE=http://localhost OUT=e2e-screens node infra/e2e/storefront.mjs
import { chromium } from "playwright";

const BASE = process.env.BASE ?? "http://localhost";
const OUT = process.env.OUT ?? "e2e-screens";
const [atPickup, pickupKey] = (await import("node:fs")).readFileSync(process.env.ORDERS_FILE ?? "/tmp/ilpacco-orders", "utf8").trim().split("\n");

async function expectText(page, text, what, timeout = 30_000) {
  try {
    await page.getByText(text, { exact: false }).first().waitFor({ timeout });
    console.log(`  ✓ ${what}`);
  } catch {
    await page.screenshot({ path: `${OUT}/failed-${what.replace(/\s+/g, "-")}.png`, fullPage: true });
    throw new Error(`Не найдено «${text}» (${what})`);
  }
}

const browser = await chromium.launch();
const errors = [];
try {
  for (const [name, viewport, mobile] of [["desktop", { width: 1440, height: 900 }, false], ["mobile", { width: 390, height: 844 }, true]]) {
    console.log(`Витрина: ${name}`);
    const ctx = await browser.newContext({ viewport, isMobile: mobile, hasTouch: mobile, locale: "ru-RU", deviceScaleFactor: mobile ? 2 : 1 });
    const page = await ctx.newPage();
    page.on("pageerror", (e) => errors.push(`${name}: ${e.message}`));

    await page.goto(`${BASE}/`);
    await expectText(page, "Обувь и трикотаж от семейных мастерских Италии", "кампания на главной");
    await expectText(page, "Италия по регионам", "регионы");
    await page.waitForTimeout(600);
    await page.screenshot({ path: `${OUT}/store-${name}-home.png`, fullPage: true });
    const width = await page.evaluate(() => document.documentElement.scrollWidth);
    if (width > viewport.width + 1) throw new Error(`${name}: горизонтальная прокрутка, ширина ${width}`);

    await page.goto(`${BASE}/catalog?category=SHOES`);
    await expectText(page, "Лоферы из замши", "каталог обуви");
    await page.locator(".fav").first().click();
    await page.screenshot({ path: `${OUT}/store-${name}-catalog.png`, fullPage: true });

    await page.locator(".pcard a", { hasText: "Лоферы из замши" }).first().click();
    await expectText(page, "Добавить в корзину", "карточка товара");
    await page.locator(".pdp-info .btn.block").first().click();
    await expectText(page, "Выберите размер", "подсказка про размер");
    await page.getByRole("button", { name: "41", exact: true }).click();
    await page.locator(".pdp-info .btn.block").first().click();
    await expectText(page, "в корзине", "тост добавления");
    await page.screenshot({ path: `${OUT}/store-${name}-product.png`, fullPage: !mobile });

    await page.goto(`${BASE}/favorites`);
    await expectText(page, "Лоферы из замши", "избранное");

    await page.goto(`${BASE}/cart`);
    await expectText(page, "Одна посылка на заказ", "корзина");
    await page.screenshot({ path: `${OUT}/store-${name}-cart.png`, fullPage: true });

    if (!mobile) {
      console.log("  оформление → оплата → заказ");
      await page.getByRole("link", { name: "Перейти к оформлению" }).click().catch(() => page.goto(`${BASE}/checkout`));
      await expectText(page, "Данные для таможни", "оформление");
      await page.fill("#name", "Анна Смирнова");
      await page.fill("#phone", "+7 916 123-45-67");
      await page.fill("#passport", "4500 123456");
      await page.fill("#inn", "770123456789");
      await page.fill("#email", "anna@example.ru");
      await page.locator(".picker-list .option").nth(1).click();
      await page.locator(".leaflet-tile-loaded").first().waitFor({ timeout: 30_000 }).catch(() => console.log("  ! тайлы карты не загрузились"));
      await page.locator("label[for=pay-sbp]").click();
      await page.screenshot({ path: `${OUT}/store-${name}-checkout.png`, fullPage: true });
      await page.getByRole("button", { name: /Перейти к оплате/ }).click();
      await expectText(page, "Отсканируйте QR-код", "страница оплаты СБП");
      await page.screenshot({ path: `${OUT}/store-${name}-pay.png`, fullPage: true });
      await page.getByRole("button", { name: /^Оплатить/ }).click();
      await expectText(page, "Оплата прошла", "заказ оплачен");
      await expectText(page, "Отменить заказ", "отмена доступна");
      await page.screenshot({ path: `${OUT}/store-${name}-order-paid.png`, fullPage: true });
      await page.goto(`${BASE}/orders`);
      await expectText(page, "Активные", "список заказов");

      console.log("  регистрация и личный кабинет");
      await page.goto(`${BASE}/account`);
      await page.waitForSelector("#username", { timeout: 60_000 });
      await page.locator("a[href*='registration']").first().click();
      const user = `anna${Date.now()}`;
      await page.fill("#username", user);
      await page.fill("#email", `${user}@example.ru`);
      await page.fill("#firstName", "Анна");
      await page.fill("#lastName", "Смирнова");
      await page.fill("#password", "Ilpacco-2026!");
      await page.fill("#password-confirm", "Ilpacco-2026!");
      await page.screenshot({ path: `${OUT}/store-${name}-register.png` });
      await Promise.all([page.waitForURL(`${BASE}/account**`, { timeout: 60_000 }), page.locator("input[type=submit], button[type=submit]").first().click()]);
      await expectText(page, "Заказы из этого браузера сохранены", "заказ привязан к аккаунту");
      await page.screenshot({ path: `${OUT}/store-${name}-account.png`, fullPage: true });
      await page.goto(`${BASE}/account/profile`);
      await expectText(page, "Уведомления о заказах", "профиль");
      await page.screenshot({ path: `${OUT}/store-${name}-profile.png`, fullPage: true });
      await page.goto(`${BASE}/account/notifications`);
      await expectText(page, "оплачен", "лента уведомлений", 70_000);
      await page.screenshot({ path: `${OUT}/store-${name}-notifications.png`, fullPage: true });
    }

    console.log("  заказ в пункте выдачи по ссылке из СМС");
    await page.goto(`${BASE}/orders/${atPickup}?key=${pickupKey}`);
    await expectText(page, "Код получения", "код получения");
    await page.locator(".photo img").first().waitFor({ timeout: 30_000 }).catch(() => undefined);
    await page.screenshot({ path: `${OUT}/store-${name}-order-pickup.png`, fullPage: true });
    await ctx.close();
  }
  if (errors.length) throw new Error(`Ошибки в браузере:\n${errors.join("\n")}`);
  console.log("OK: витрина работает на компьютере и телефоне");
} finally {
  await browser.close();
}
