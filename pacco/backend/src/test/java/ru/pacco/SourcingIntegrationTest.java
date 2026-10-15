package ru.pacco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import ru.pacco.sourcing.ParsersTest;
import ru.pacco.sourcing.internal.Fetcher;

/** Импорт Zalando на синтетических страницах: загрузка → модерация → каталог → наличие; фид CSV; комплаенс. */
@SpringBootTest
class SourcingIntegrationTest {

    static final Map<String, String> PAGES = new ConcurrentHashMap<>();
    static final String LISTING = "https://www.zalando.it/scarpe-uomo/";
    static final String GEOX = "https://www.zalando.it/geox-u-spherica-sneakers-basse-GE112O0H5-Q11.html";
    static final String DOUCALS = "https://www.zalando.it/doucals-mocassini-cognac-DOH12M00D-O11.html";

    @TestConfiguration
    static class StubSite {
        @Bean
        @Primary
        Fetcher stubFetcher() {
            return url -> {
                String body = PAGES.get(url);
                if (body == null) throw new Fetcher.FetchException("Ответ 404", 404);
                return body;
            };
        }
    }

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        PAGES.put("https://www.zalando.it/robots.txt", ParsersTest.fixture("robots.txt"));
        PAGES.put(LISTING, ParsersTest.fixture("listing.html"));
        PAGES.put(LISTING + "?p=2", ParsersTest.fixture("listing.html"));
        PAGES.put(GEOX, ParsersTest.fixture("product-geox.html"));
        PAGES.put(DOUCALS, ParsersTest.fixture("product-doucals.html"));
    }

    @Test
    void importModerateRefreshAndFeed() throws Exception {
        mvc.perform(post("/api/admin/import/jobs").with(role("support")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + LISTING + "\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/import/jobs").with(role("moderator")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/\"}")).andExpect(jsonPath("$.title").value("bad_url"));

        String started = mvc.perform(post("/api/admin/import/jobs").with(role("moderator")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + LISTING + "\",\"maxItems\":10}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        Integer job = JsonPath.read(started, "$.id");
        String done = null;
        for (int i = 0; i < 100; i++) {
            done = mvc.perform(get("/api/admin/import/jobs/{id}", job).with(role("moderator"))).andReturn().getResponse().getContentAsString();
            if (!"RUNNING".equals(JsonPath.read(done, "$.status"))) break;
            Thread.sleep(100);
        }
        assertThat((String) JsonPath.read(done, "$.status")).isEqualTo("DONE");
        assertThat((Integer) JsonPath.read(done, "$.found")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(done, "$.imported")).isEqualTo(2);

        String items = mvc.perform(get("/api/admin/import/items").param("status", "NEW").with(role("moderator")))
                .andReturn().getResponse().getContentAsString();
        List<Integer> geox = JsonPath.read(items, "$[?(@.url == '" + GEOX + "')].id");
        List<String> hintCat = JsonPath.read(items, "$[?(@.url == '" + GEOX + "')].hintCategory");
        assertThat(hintCat).containsExactly("SHOES");

        mvc.perform(post("/api/admin/import/items/{id}/approve", geox.getFirst()).with(role("moderator"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"SHOES\",\"hsCode\":\"6404\"}"))
                .andExpect(jsonPath("$.title").value("hs_required"));
        String approved = mvc.perform(post("/api/admin/import/items/{id}/approve", geox.getFirst()).with(role("moderator"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"SHOES\",\"hsCode\":\"6404 1990\"}"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.productStatus").value("PUBLISHED"))
                .andReturn().getResponse().getContentAsString();
        Integer productId = JsonPath.read(approved, "$.productId");
        mvc.perform(get("/api/products/{id}", productId))
                .andExpect(jsonPath("$.sellerName").value("Geox"))
                .andExpect(jsonPath("$.priceEurCents").value(13200))       // 119,95 € + 10 %, вверх до 0,10 €
                .andExpect(jsonPath("$.sizes.length()").value(2));

        // у поставщика закончился товар → снимаем с продажи
        PAGES.put(GEOX, ParsersTest.fixture("product-geox.html").replace("schema.org/InStock", "schema.org/OutOfStock"));
        mvc.perform(post("/api/admin/import/items/{id}/refresh", geox.getFirst()).with(role("moderator")))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.productStatus").value("BLOCKED"));
        mvc.perform(get("/api/products/{id}", productId)).andExpect(status().isNotFound());
        // снова в наличии → возвращаем
        PAGES.put(GEOX, ParsersTest.fixture("product-geox.html"));
        mvc.perform(post("/api/admin/import/items/{id}/refresh", geox.getFirst()).with(role("moderator")))
                .andExpect(jsonPath("$.productStatus").value("PUBLISHED"));

        // фид партнёрской программы: сумка дороже 290 € не проходит комплаенс
        String feed = mvc.perform(multipart("/api/admin/import/feed")
                        .file(new MockMultipartFile("file", "feed.csv", "text/csv", ParsersTest.fixture("feed.csv").getBytes()))
                        .with(role("admin")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.found").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(feed, "$.kind")).isEqualTo("FEED");
        String fresh = mvc.perform(get("/api/admin/import/items").param("status", "NEW").with(role("moderator")))
                .andReturn().getResponse().getContentAsString();
        List<Integer> bag = JsonPath.read(fresh, "$[?(@.brand == 'Borbonese')].id");
        mvc.perform(post("/api/admin/import/items/{id}/approve", bag.getFirst()).with(role("moderator"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"CLOTHES\",\"hsCode\":\"42022100\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("compliance_failed"));
        mvc.perform(post("/api/admin/import/items/{id}/reject", bag.getFirst()).with(role("moderator"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Дороже 290 €\"}"))
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    private static RequestPostProcessor role(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
