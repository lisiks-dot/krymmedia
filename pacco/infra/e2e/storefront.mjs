// Браузерная проверка витрины: главная, каталог, карточка, избранное, корзина — на компьютере и телефоне.
// Запуск: BASE=http://localhost OUT=e2e-screens node infra/e2e/storefront.mjs
import { chromium } from "playwright";

const BASE = process.env.BASE ?? "http://localhost";
const OUT = process.env.OUT ?? "e2e-screens";

async function expectText(page, text, what) {
  try {
    await page.getByText(text, { exact: false }).first().waitFor({ timeout: 30_000 });
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
    await ctx.close();
  }
  if (errors.length) throw new Error(`Ошибки в браузере:\n${errors.join("\n")}`);
  console.log("OK: витрина работает на компьютере и телефоне");
} finally {
  await browser.close();
}
