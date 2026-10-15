package ru.pacco.sourcing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import ru.pacco.sourcing.internal.FeedCsvParser;
import ru.pacco.sourcing.internal.Hints;
import ru.pacco.sourcing.internal.ZalandoParser;
import ru.pacco.shared.Category;

/** Разбор на синтетических страницах в формате schema.org/Open Graph — не на копиях страниц Zalando. */
public class ParsersTest {

    public static String fixture(String name) throws IOException {
        try (var in = ParsersTest.class.getResourceAsStream("/zalando/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void listingGivesUniqueCanonicalProductLinks() throws IOException {
        List<String> links = ZalandoParser.productLinks(fixture("listing.html"), "https://www.zalando.it/scarpe-uomo/");
        assertThat(links).containsExactly(
                "https://www.zalando.it/geox-u-spherica-sneakers-basse-GE112O0H5-Q11.html",
                "https://www.zalando.it/doucals-mocassini-cognac-DOH12M00D-O11.html");
        assertThat(ZalandoParser.page("https://www.zalando.it/scarpe-uomo/?q=geox", 2)).isEqualTo("https://www.zalando.it/scarpe-uomo/?q=geox&p=2");
        assertThat(ZalandoParser.page("https://www.zalando.it/scarpe-uomo/?p=3", 1)).isEqualTo("https://www.zalando.it/scarpe-uomo/");
        assertThat(ZalandoParser.isProductUrl(links.getFirst())).isTrue();
    }

    @Test
    void productFromJsonLd() throws IOException {
        Scraped s = ZalandoParser.product(fixture("product-geox.html"),
                "https://www.zalando.it/geox-u-spherica-sneakers-basse-GE112O0H5-Q11.html?size=41").orElseThrow();
        assertThat(s.brand()).isEqualTo("Geox");
        assertThat(s.title()).isEqualTo("U SPHERICA - Sneakers basse");
        assertThat(s.priceEurCents()).isEqualTo(11995L);
        assertThat(s.sizes()).containsExactly("41", "43");
        assertThat(s.available()).isTrue();
        assertThat(s.sku()).isEqualTo("GE112O0H5-Q11");
        assertThat(s.url()).isEqualTo("https://www.zalando.it/geox-u-spherica-sneakers-basse-GE112O0H5-Q11.html");
        assertThat(s.categoryHint()).contains("Scarpe");
        assertThat(Hints.of(s.title(), s.categoryHint()).category()).isEqualTo(Category.SHOES);
    }

    @Test
    void productFromOpenGraphFallback() throws IOException {
        Scraped s = ZalandoParser.product(fixture("product-doucals.html"),
                "https://www.zalando.it/doucals-mocassini-cognac-DOH12M00D-O11.html").orElseThrow();
        assertThat(s.brand()).isEqualTo("Doucal's");
        assertThat(s.priceEurCents()).isEqualTo(24500L);
        assertThat(Hints.of(s.title(), s.categoryHint()).hsHeading()).isEqualTo("6403");
    }

    @Test
    void feedGroupsSizesAndHandlesQuotes() throws IOException {
        List<Scraped> rows = FeedCsvParser.parse(fixture("feed.csv"));
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().sizes()).containsExactly("M", "L");
        assertThat(rows.getFirst().priceEurCents()).isEqualTo(8990L);
        assertThat(rows.get(1).title()).isEqualTo("Borsa a mano \"Diana\"");
    }
}
