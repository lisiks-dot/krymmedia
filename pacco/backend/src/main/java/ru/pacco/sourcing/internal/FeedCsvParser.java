package ru.pacco.sourcing.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ru.pacco.sourcing.Scraped;

/**
 * Фид товаров партнёрской программы (Zalando Partner Program через Awin и аналоги) в CSV.
 * Колонки ищем по распространённым названиям; разделитель — запятая, точка с запятой, «|» или табуляция.
 * Строки одного товара в разных размерах склеиваются по артикулу.
 */
public final class FeedCsvParser {

    private static final Map<String, List<String>> ALIASES = Map.of(
            "url", List.of("merchant_deep_link", "aw_deep_link", "deep_link", "product_url", "link", "url"),
            "sku", List.of("merchant_product_id", "sku", "article_number", "product_id", "id"),
            "title", List.of("product_name", "title", "name"),
            "brand", List.of("brand_name", "brand"),
            "price", List.of("search_price", "store_price", "price", "sale_price"),
            "currency", List.of("currency", "price_currency"),
            "category", List.of("merchant_category", "category_name", "product_type", "category"),
            "color", List.of("colour", "color"),
            "size", List.of("size", "sizes", "Fashion:size"),
            "stock", List.of("in_stock", "stock_status", "availability"));

    private FeedCsvParser() {
    }

    public static List<Scraped> parse(String csv) {
        List<List<String>> rows = rows(csv);
        if (rows.isEmpty()) return List.of();
        List<String> head = rows.getFirst().stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
        Map<String, Integer> col = new HashMap<>();
        ALIASES.forEach((key, names) -> {
            for (String n : names) {
                int i = head.indexOf(n.toLowerCase(Locale.ROOT));
                if (i >= 0) {
                    col.put(key, i);
                    break;
                }
            }
        });
        if (!col.containsKey("url") || !col.containsKey("title")) {
            throw new IllegalArgumentException("В фиде нет колонок со ссылкой на товар и названием");
        }
        Map<String, Scraped> byKey = new java.util.LinkedHashMap<>();
        for (List<String> r : rows.subList(1, rows.size())) {
            String url = get(r, col, "url");
            String title = get(r, col, "title");
            if (url == null || title == null) continue;
            String sku = get(r, col, "sku");
            String key = sku != null ? sku.replaceAll("[-_]?\\d{3,}$", "") : url;
            String stock = get(r, col, "stock");
            boolean inStock = stock == null || stock.matches("(?i)1|true|yes|in.?stock|si|sì");
            String size = get(r, col, "size");
            Scraped prev = byKey.get(key);
            List<String> sizes = new ArrayList<>(prev == null ? List.of() : prev.sizes());
            if (size != null && inStock) {
                for (String s : size.split("[,|]")) if (!s.isBlank() && !sizes.contains(s.trim())) sizes.add(s.trim());
            }
            Long price = ZalandoParser.cents(get(r, col, "price"));
            if (prev != null && prev.priceEurCents() != null && (price == null || prev.priceEurCents() < price)) price = prev.priceEurCents();
            byKey.put(key, new Scraped(prev != null ? prev.url() : url, sku, get(r, col, "brand"), title, get(r, col, "color"),
                    get(r, col, "category"), price, orDefault(get(r, col, "currency"), "EUR"), sizes,
                    (prev != null && prev.available()) || inStock, null));
        }
        return new ArrayList<>(byKey.values());
    }

    private static String get(List<String> row, Map<String, Integer> col, String key) {
        Integer i = col.get(key);
        if (i == null || i >= row.size()) return null;
        String v = row.get(i).trim();
        return v.isEmpty() ? null : v;
    }

    private static String orDefault(String v, String d) {
        return v == null ? d : v;
    }

    /** Минимальный разбор CSV: кавычки, экранирование "" и переводы строк внутри кавычек. */
    static List<List<String>> rows(String text) {
        String first = text.lines().findFirst().orElse("");
        char sep = Arrays.asList(';', '\t', '|', ',').stream()
                .max((a, b) -> Long.compare(count(first, a), count(first, b))).orElse(',');
        List<List<String>> out = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else if (c == '"') quoted = false;
                else cell.append(c);
            } else if (c == '"') {
                quoted = true;
            } else if (c == sep) {
                row.add(cell.toString()); cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString()); cell.setLength(0);
                if (!(row.size() == 1 && row.getFirst().isEmpty())) out.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) { row.add(cell.toString()); out.add(row); }
        return out;
    }

    private static long count(String s, char c) {
        return s.chars().filter(x -> x == c).count();
    }
}
