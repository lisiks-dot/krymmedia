package ru.pacco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Сквозной сценарий на настоящих PostgreSQL и Redis (в CI — сервисы GitHub Actions,
 * локально — {@code docker compose up -d postgres redis}).
 */
@SpringBootTest
class FlowIntegrationTest {

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }

    @Test
    void buyerGoesFromCatalogToPaidOrder() throws Exception {
        String products = mvc.perform(get("/api/products")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer loafers = id(products, "Лоферы из замши, мужские");
        Integer oil = id(products, "Масло Extra Vergine, 0,75 л");

        String cartId = JsonPath.read(mvc.perform(post("/api/carts")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/carts/{id}/items", cartId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + loafers + "}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("size_required"));

        mvc.perform(post("/api/carts/{id}/items", cartId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + loafers + ",\"size\":\"41\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/carts/{id}/items", cartId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + oil + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quote.totalRub").value(23140))
                .andExpect(jsonPath("$.quote.dutyRub").value(0));

        String order = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"cartId":"%s","mode":"GROUND","buyerName":"Анна Смирнова","phone":"+7 916 000-00-00",
                         "passport":"45 00 123456","inn":"770000000000","pickupPoint":"СДЭК · Москва, Покровка, 12",
                         "consent":true}""".formatted(cartId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalRub").value(23140))
                .andReturn().getResponse().getContentAsString();
        String number = JsonPath.read(order, "$.number");
        String key = JsonPath.read(order, "$.accessKey");

        mvc.perform(get("/api/carts/{id}", cartId)).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(post("/api/orders/{n}/pay", number)).andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/{n}/pay", number).header("X-Order-Key", key)).andExpect(jsonPath("$.status").value("PAID"));

        mvc.perform(post("/api/admin/orders/{n}/advance", number)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/orders/{n}/advance", number).with(role("support")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.statusStep").value(3));
    }

    @Test
    void foodOnlyOrderIsRejected() throws Exception {
        String products = mvc.perform(get("/api/products").param("category", "FOOD"))
                .andReturn().getResponse().getContentAsString();
        String cartId = JsonPath.read(mvc.perform(post("/api/carts")).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/carts/{id}/items", cartId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id(products, "Кофе в зёрнах, 1 кг") + "}"))
                .andExpect(jsonPath("$.quote.foodOnly").value(true));

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"cartId":"%s","buyerName":"Анна","phone":"79160000000","passport":"4500123456",
                         "inn":"770000000000","pickupPoint":"СДЭК","consent":true}""".formatted(cartId)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("food_only"));
    }

    @Test
    void loweringThresholdTakesExpensiveShoesOffSaleAndRestoringBringsThemBack() throws Exception {
        mvc.perform(get("/api/admin/compliance/rules").with(role("support"))).andExpect(status().isForbidden());

        int before = count();
        String lowered = """
                {"thresholdEurCents":15000,"thresholdCategories":["SHOES","CLOTHES","BEAUTY","HOME"],
                 "embargoPrefixes":["02","03","04","07","08","1601"],"alcoholPrefixes":["2203","2204","2205","2206","2208"]}""";
        mvc.perform(put("/api/admin/compliance/rules").with(role("admin"))
                .contentType(MediaType.APPLICATION_JSON).content(lowered)).andExpect(status().isOk());
        assertThat(count()).isEqualTo(before - 2);   // лоферы 158 € и ботильоны 214 €

        mvc.perform(put("/api/admin/compliance/rules").with(role("admin"))
                .contentType(MediaType.APPLICATION_JSON).content(lowered.replace("15000", "29000")))
                .andExpect(status().isOk());
        assertThat(count()).isEqualTo(before);
    }

    @Test
    void moderatorCannotPublishCheese() throws Exception {
        mvc.perform(post("/api/admin/products").with(role("moderator")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"sellerName":"Caseificio","title":"Parmigiano Reggiano 24 mesi","category":"FOOD",
                         "hsCode":"04069021","priceEurCents":3200,"weightGrams":1000}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("compliance_failed"));
    }

    private int count() throws Exception {
        List<?> list = JsonPath.read(mvc.perform(get("/api/products")).andReturn().getResponse().getContentAsString(), "$");
        return list.size();
    }

    private static Integer id(String json, String title) {
        List<Integer> ids = JsonPath.read(json, "$[?(@.title == '" + title + "')].id");
        assertThat(ids).as(title).hasSize(1);
        return ids.getFirst();
    }

    private static RequestPostProcessor role(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
