package ru.pacco.sourcing.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import tools.jackson.databind.json.JsonMapper;

import ru.pacco.sourcing.Scraped;

/**
 * Разбор страниц Zalando: ссылки на товары со страницы каталога и данные товара из разметки schema.org
 * (JSON-LD Product), запасной вариант — мета-теги Open Graph. Верстка у поставщика меняется:
 * если разбор перестал находить товары — первым делом проверьте эти два источника на живой странице.
 */
public final class ZalandoParser {

    /** Товар Zalando: …-{SKU 9 знаков}-{3 знака}.html, например /nike-air-force-1-NI112O0BG-A11.html */
    static final Pattern PRODUCT_PATH = Pattern.compile("/[a-z0-9\\-]+-([A-Z0-9]{9}-[A-Z0-9]{3})\\.html");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ZalandoParser() {
    }

    public static boolean isProductUrl(String url) {
        return PRODUCT_PATH.matcher(URI.create(url).getPath()).matches();
    }

    /** Ссылки на карточки товаров со страницы каталога, бренда или поиска; без дублей, в порядке появления. */
    public static List<String> productLinks(String html, String pageUrl) {
        Document doc = Jsoup.parse(html, pageUrl);
        Set<String> out = new LinkedHashSet<>();
        for (Element a : doc.select("a[href]")) {
            String abs = a.absUrl("href");
            if (abs.isEmpty()) continue;
            URI uri = URI.create(abs.replace(" ", "%20"));
            if (uri.getHost() == null || !uri.getHost().endsWith("zalando.it")) continue;
            if (PRODUCT_PATH.matcher(uri.getPath()).matches()) {
                out.add("https://www.zalando.it" + uri.getPath());
            }
        }
        return new ArrayList<>(out);
    }

    /** Адрес следующей страницы каталога: параметр p=N. */
    public static String page(String listingUrl, int n) {
        String base = listingUrl.replaceAll("([?&])p=\\d+&?", "$1").replaceAll("[?&]$", "");
        return n <= 1 ? base : base + (base.contains("?") ? "&" : "?") + "p=" + n;
    }

    @SuppressWarnings("unchecked")
    public static Optional<Scraped> product(String html, String url) {
        Document doc = Jsoup.parse(html, url);
        Map<String, Object> product = null;
        for (Element s : doc.select("script[type=application/ld+json]")) {
            try {
                Object parsed = JSON.readValue(s.data(), Object.class);
                product = findProduct(parsed);
                if (product != null) break;
            } catch (RuntimeException ignored) {
                // битый JSON-LD — пробуем следующий блок
            }
        }
        String sku = skuFromUrl(url);
        if (product != null) {
            String title = str(product.get("name"));
            Object brandNode = product.get("brand");
            String brand = brandNode instanceof Map<?, ?> m ? str(((Map<String, Object>) m).get("name")) : str(brandNode);
            String color = str(product.get("color"));
            String image = firstString(product.get("image"));
            List<Map<String, Object>> offers = offers(product.get("offers"));
            Long price = null;
            String currency = null;
            boolean available = offers.isEmpty();
            List<String> sizes = new ArrayList<>();
            for (Map<String, Object> o : offers) {
                Long p = cents(o.get("price") != null ? o.get("price") : o.get("lowPrice"));
                if (p != null && (price == null || p < price)) price = p;
                if (currency == null) currency = str(o.get("priceCurrency"));
                String avail = str(o.get("availability"));
                boolean inStock = avail == null || avail.endsWith("InStock") || avail.endsWith("LimitedAvailability");
                available |= inStock;
                String size = sizeOf(o);
                if (size != null && inStock && !sizes.contains(size)) sizes.add(size);
            }
            if (title != null) {
                return Optional.of(new Scraped(canonical(url), sku != null ? sku : str(product.get("sku")), brand, title, color,
                        breadcrumbs(doc, url), price, currency == null ? "EUR" : currency, sizes, available, image));
            }
        }
        // запасной вариант — Open Graph
        String title = meta(doc, "og:title");
        if (title == null) return Optional.empty();
        Long price = cents(meta(doc, "product:price:amount"));
        return Optional.of(new Scraped(canonical(url), sku, meta(doc, "product:brand"), title, null, breadcrumbs(doc, url),
                price, Optional.ofNullable(meta(doc, "product:price:currency")).orElse("EUR"), List.of(), true, meta(doc, "og:image")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findProduct(Object node) {
        if (node instanceof Map<?, ?> m) {
            Map<String, Object> map = (Map<String, Object>) m;
            Object type = map.get("@type");
            if ("Product".equals(type) || (type instanceof Collection<?> c && c.contains("Product"))) return map;
            Object graph = map.get("@graph");
            if (graph != null) return findProduct(graph);
        } else if (node instanceof Collection<?> list) {
            for (Object x : list) {
                Map<String, Object> found = findProduct(x);
                if (found != null) return found;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> offers(Object node) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (node instanceof Map<?, ?> m) {
            Map<String, Object> map = (Map<String, Object>) m;
            if (map.get("offers") != null) {          // AggregateOffer с вложенными предложениями по размерам
                out.addAll(offers(map.get("offers")));
                if (out.isEmpty()) out.add(map);
            } else {
                out.add(map);
            }
        } else if (node instanceof Collection<?> list) {
            for (Object x : list) out.addAll(offers(x));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static String sizeOf(Map<String, Object> offer) {
        for (String key : List.of("size", "name")) {
            String v = str(offer.get(key));
            if (v != null && v.length() <= 12) return v.replace("EU ", "").trim();
        }
        Object item = offer.get("itemOffered");
        if (item instanceof Map<?, ?> m) {
            String v = str(((Map<String, Object>) m).get("size"));
            if (v != null && v.length() <= 12) return v.trim();
        }
        return null;
    }

    /** Подсказка категории: хлебные крошки (BreadcrumbList) и путь адреса. */
    private static String breadcrumbs(Document doc, String url) {
        List<String> parts = new ArrayList<>();
        for (Element s : doc.select("script[type=application/ld+json]")) {
            String data = s.data();
            if (!data.contains("BreadcrumbList")) continue;
            Matcher m = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]{1,60})\"").matcher(data);
            while (m.find()) parts.add(m.group(1));
        }
        parts.add(URI.create(url).getPath());
        return String.join(" / ", parts);
    }

    private static String meta(Document doc, String property) {
        Element e = doc.selectFirst("meta[property=" + property + "], meta[name=" + property + "]");
        String v = e == null ? null : e.attr("content").trim();
        return v == null || v.isEmpty() ? null : v;
    }

    static Long cents(Object value) {
        String s = str(value);
        if (s == null) return null;
        s = s.replaceAll("[^0-9,.]", "");
        if (s.isEmpty()) return null;
        if (s.contains(",") && s.contains(".")) s = s.replace(".", "").replace(",", ".");
        else if (s.contains(",")) s = s.replace(",", ".");
        try {
            return new BigDecimal(s).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private static String skuFromUrl(String url) {
        Matcher m = PRODUCT_PATH.matcher(URI.create(url).getPath());
        return m.matches() ? m.group(1) : null;
    }

    private static String canonical(String url) {
        URI u = URI.create(url);
        return u.getScheme() + "://" + u.getHost() + u.getPath();
    }

    private static String firstString(Object node) {
        if (node instanceof Collection<?> c) return c.isEmpty() ? null : firstString(c.iterator().next());
        if (node instanceof Map<?, ?> m) return str(m.get("url"));
        return str(node);
    }

    static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }
}
