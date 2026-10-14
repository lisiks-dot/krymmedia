package ru.pacco;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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

/** Продавец: заявка → одобрение → карточка → модерация → заказ → подтверждение → выплаты. */
@SpringBootTest
class SellerFlowIntegrationTest {

    /** Демо-продавец Calzoleria Verdi из миграции V3. */
    private static final String DEMO_SELLER = "11111111-1111-4111-8111-111111111111";

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }

    @Test
    void newSellerIsModeratedBeforeSelling() throws Exception {
        String sub = "test-" + UUID.randomUUID();
        String piva = "IT" + String.format("%011d", ThreadLocalRandom.current().nextLong(1, 99_999_999_999L));

        mvc.perform(get("/api/seller/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/seller/me").with(user(sub))).andExpect(status().isNotFound());
        mvc.perform(post("/api/seller/products").with(user(sub)).contentType(MediaType.APPLICATION_JSON)
                        .content(product("Camicia", "CLOTHES", "62052000", 8900)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("seller_missing"));

        mvc.perform(post("/api/seller/me").with(user(sub)).contentType(MediaType.APPLICATION_JSON)
                        .content(application("Camiceria Test", "IT123")))
                .andExpect(status().isBadRequest());

        String applied = mvc.perform(post("/api/seller/me").with(user(sub)).contentType(MediaType.APPLICATION_JSON)
                        .content(application("Camiceria Test", piva)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        Integer sellerId = JsonPath.read(applied, "$.id");

        mvc.perform(post("/api/seller/products").with(user(sub)).contentType(MediaType.APPLICATION_JSON)
                        .content(product("Camicia", "CLOTHES", "62052000", 8900)))
                .andExpect(jsonPath("$.title").value("seller_not_active"));

        mvc.perform(post("/api/admin/sellers/{id}/approve", sellerId).with(role("support"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/sellers/{id}/approve", sellerId).with(role("moderator")))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mvc.perform(post("/api/seller/products").with(user(sub)).contentType(MediaType.APPLICATION_JSON)
                        .content(product("Cappotto in cashmere", "CLOTHES", "62021100", 42000)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("compliance_failed"));

        String created = mvc.perform(post("/api/seller/products").with(user(sub)).contentType(MediaType.APPLICATION_JSON)
                        .content(product("Camicia di lino", "CLOTHES", "62052000", 8900)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.sellerName").value("Camiceria Test"))
                .andReturn().getResponse().getContentAsString();
        Integer productId = JsonPath.read(created, "$.id");

        mvc.perform(get("/api/products/{id}", productId)).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/products/{id}/approve", productId).with(role("moderator")))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(get("/api/products/{id}", productId)).andExpect(status().isOk());

        mvc.perform(put("/api/seller/products/{id}/price", productId).with(user(sub)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceEurCents\":35000}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(put("/api/seller/products/{id}/price", productId).with(user(DEMO_SELLER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceEurCents\":9900}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sellerConfirmsOwnLinesAndSeesPendingPayout() throws Exception {
        String products = mvc.perform(get("/api/products")).andReturn().getResponse().getContentAsString();
        List<Integer> loafers = JsonPath.read(products, "$[?(@.title == 'Лоферы из замши, мужские')].id");
        List<Integer> oil = JsonPath.read(products, "$[?(@.title == 'Масло Extra Vergine, 0,75 л')].id");

        String before = mvc.perform(get("/api/seller/payouts").with(user(DEMO_SELLER)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Integer pendingBefore = JsonPath.read(before, "$.pendingGrossEurCents");

        String cartId = JsonPath.read(mvc.perform(post("/api/carts")).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/carts/{id}/items", cartId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + loafers.getFirst() + ",\"size\":\"42\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/carts/{id}/items", cartId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + oil.getFirst() + "}")).andExpect(status().isOk());
        String number = JsonPath.read(mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"cartId":"%s","buyerName":"Иван Петров","phone":"79160000001","passport":"4500654321",
                         "inn":"770000000001","pickupPoint":"СДЭК · Москва","consent":true}""".formatted(cartId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.number");

        mvc.perform(post("/api/seller/orders/{n}/confirm", number).with(user(DEMO_SELLER)))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/orders/{n}/pay", number)).andExpect(jsonPath("$.status").value("PAID"));

        mvc.perform(get("/api/seller/orders").with(user(DEMO_SELLER)))
                .andExpect(jsonPath("$[?(@.number == '" + number + "')].confirmedBySeller").value(false))
                .andExpect(jsonPath("$[?(@.number == '" + number + "')].lines.length()").value(1));

        mvc.perform(post("/api/seller/orders/{n}/confirm", number).with(user(DEMO_SELLER)))
                .andExpect(jsonPath("$.confirmedBySeller").value(true))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mvc.perform(get("/api/seller/payouts").with(user(DEMO_SELLER)))
                .andExpect(jsonPath("$.pendingGrossEurCents").value(pendingBefore + 15800));
    }

    private static String application(String name, String piva) {
        return """
                {"legalName":"%s","partitaIva":"%s","iban":"IT60X0542811101000000654321",
                 "region":"Ломбардия","contactEmail":"info@example.it"}""".formatted(name, piva);
    }

    private static String product(String title, String category, String hs, long cents) {
        return """
                {"title":"%s","category":"%s","hsCode":"%s","priceEurCents":%d,"weightGrams":300,
                 "sizes":[],"description":"Тест"}""".formatted(title, category, hs, cents);
    }

    private static RequestPostProcessor user(String sub) {
        return jwt().jwt(j -> j.subject(sub));
    }

    private static RequestPostProcessor role(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
