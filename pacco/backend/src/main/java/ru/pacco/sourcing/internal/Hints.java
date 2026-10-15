package ru.pacco.sourcing.internal;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import ru.pacco.shared.Category;

/**
 * Подсказки модератору по категории, товарной позиции ТН ВЭД (первые 4 знака) и весу.
 * Это только подсказки: 8-значный код ТН ВЭД модератор вводит сам — от него зависит таможня.
 */
public final class Hints {

    public record Hint(Category category, String hsHeading, int weightGrams, String kind) {
    }

    /** Ключевые слова (итальянский) → категория, товарная позиция, вес в граммах. Порядок важен: частное раньше общего. */
    private static final Map<String, Hint> RULES = new LinkedHashMap<>();

    static {
        rule("sneaker|scarpe da ginnastica|running", Category.SHOES, "6404", 900, "кроссовки");
        rule("stival|anfibi|chelsea", Category.SHOES, "6403", 1400, "сапоги и ботинки");
        rule("sandal|ciabatt|infradit", Category.SHOES, "6402", 500, "сандалии");
        rule("mocassin|loafer|derby|stringat|décolleté|decollete|ballerin|scarpe|calzature", Category.SHOES, "6403", 1100, "обувь");
        rule("piumino|cappotto|giacca|giubbotto|parka|blazer", Category.CLOTHES, "6201", 1200, "верхняя одежда");
        rule("jeans|pantalon|chino|shorts|bermuda", Category.CLOTHES, "6203", 600, "брюки");
        rule("maglion|cardigan|pullover|felpa|maglia", Category.CLOTHES, "6110", 450, "трикотаж");
        rule("t-shirt|t shirt|polo|top|canotta", Category.CLOTHES, "6109", 220, "футболки");
        rule("camicia|camicett|blusa", Category.CLOTHES, "6205", 280, "рубашки");
        rule("vestit|abito|gonna", Category.CLOTHES, "6204", 450, "платья и юбки");
        rule("borsa|zaino|portafogl|pochette", Category.CLOTHES, "4202", 700, "сумки");
        rule("profum|eau de", Category.BEAUTY, "3303", 400, "парфюмерия");
        rule("crema|siero|trucco|mascara|rossetto|skincare|beauty|cosmet", Category.BEAUTY, "3304", 250, "косметика");
        rule("shampoo|balsamo|capelli", Category.BEAUTY, "3305", 400, "уход за волосами");
        rule("lenzuol|tovagli|asciugaman|cuscin|casa", Category.HOME, "6302", 900, "текстиль для дома");
        rule("abbigliamento", Category.CLOTHES, "6204", 500, "одежда");
    }

    private Hints() {
    }

    private static void rule(String keys, Category c, String hs, int grams, String kind) {
        RULES.put(keys, new Hint(c, hs, grams, kind));
    }

    public static Hint of(String... texts) {
        String all = String.join(" ", java.util.Arrays.stream(texts).map(t -> t == null ? "" : t).toList()).toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Hint> e : RULES.entrySet()) {
            for (String key : e.getKey().split("\\|")) {
                if (all.contains(key)) return e.getValue();
            }
        }
        return new Hint(Category.CLOTHES, "6204", 500, "одежда");
    }
}
