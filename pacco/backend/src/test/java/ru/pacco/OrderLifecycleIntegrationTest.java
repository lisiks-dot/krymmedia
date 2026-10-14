package ru.pacco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Полный путь заказа: оплата → продавец подтверждает и отправляет → хаб принимает, упаковывает, передаёт →
 * таможня → ПВЗ с кодом → выдача → спор → возврат → завершение. И ветки отмены.
 */
@SpringBootTest
class OrderLifecycleIntegrationTest {

    private static final String DEMO_SELLER = "11111111-1111-4111-8111-111111111111";
    private static final String KEY = "X-Order-Key";

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }

    @Test
    void orderTravelsFromPaymentToPickupAndDispute() throws Exception {
        String[] order = order();
        String n = order[0], key = order[1];

        mvc.perform(post("/api/orders/{n}/pay", n).header(KEY, key).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"SBP\"}"))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentMethod").value("SBP"))
                .andExpect(jsonPath("$.canCancel").value(true));

        // продавец: отправить до подтверждения нельзя
        mvc.perform(post("/api/seller/orders/{n}/ship", n).with(user(DEMO_SELLER)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tracking\":\"BRT123\"}")).andExpect(status().isUnprocessableContent());
        mvc.perform(get("/api/seller/orders/{n}", n).with(user(DEMO_SELLER)))
                .andExpect(jsonPath("$.needsAnswer").value(true))
                .andExpect(jsonPath("$.lines[0].lineNo").value(0));
        mvc.perform(post("/api/seller/orders/{n}/confirm", n).with(user(DEMO_SELLER)))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.needsShipping").value(true));
        mvc.perform(post("/api/seller/orders/{n}/ship", n).with(user(DEMO_SELLER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tracking\":\"brt-0042\"}"))
                .andExpect(jsonPath("$.status").value("TO_HUB"))
                .andExpect(jsonPath("$.lines[0].tracking").value("BRT-0042"));

        // после отправки на хаб покупатель уже не отменяет
        mvc.perform(post("/api/orders/{n}/cancel", n).header(KEY, key)).andExpect(status().isUnprocessableContent());

        // хаб
        mvc.perform(post("/api/ops/orders/{n}/pack", n).with(role("support")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"weightGrams\":1300}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/ops/queues/hub").with(role("ops")))
                .andExpect(jsonPath("$[?(@.number == '" + n + "')]").isNotEmpty());
        mvc.perform(post("/api/ops/orders/{n}/accept", n).with(role("ops")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"lineNo\":0,\"ok\":true}]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("lines_unchecked"));
        mvc.perform(post("/api/ops/orders/{n}/accept", n).with(role("ops")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"lineNo\":0,\"ok\":true},{\"lineNo\":1,\"ok\":true,\"note\":\"Целая бутылка\"}]}"))
                .andExpect(jsonPath("$.status").value("AT_HUB"))
                .andExpect(jsonPath("$.items[1].state").value("ACCEPTED"));
        mvc.perform(post("/api/ops/orders/{n}/pack", n).with(role("ops")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"weightGrams\":1300}")).andExpect(jsonPath("$.parcelWeightG").value(1300));
        mvc.perform(post("/api/ops/orders/{n}/handover", n).with(role("ops")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tracking\":\"cdek-77001\"}")).andExpect(jsonPath("$.carrierTracking").value("CDEK-77001"));

        // таможня и ПВЗ
        mvc.perform(post("/api/ops/orders/{n}/customs", n).with(role("ops"))).andExpect(jsonPath("$.status").value("IN_CUSTOMS"));
        mvc.perform(post("/api/ops/orders/{n}/release", n).with(role("ops"))).andExpect(jsonPath("$.status").value("RELEASED"));
        mvc.perform(post("/api/ops/orders/{n}/arrive", n).with(role("ops")))
                .andExpect(jsonPath("$.status").value("AT_PICKUP_POINT"))
                .andExpect(jsonPath("$.pickupCode").doesNotExist())
                .andExpect(jsonPath("$.storageUntil").exists());

        mvc.perform(get("/api/orders/{n}", n)).andExpect(jsonPath("$.pickupCode").doesNotExist())
                .andExpect(jsonPath("$.buyerName").value("Мария С."));
        String code = JsonPath.read(mvc.perform(get("/api/orders/{n}", n).header(KEY, key))
                .andReturn().getResponse().getContentAsString(), "$.pickupCode");
        assertThat(code).hasSize(6);

        mvc.perform(post("/api/ops/orders/{n}/issue", n).with(role("ops")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + (code.equals("000000") ? "111111" : "000000") + "\"}"))
                .andExpect(jsonPath("$.title").value("bad_code"));
        mvc.perform(post("/api/ops/orders/{n}/issue", n).with(role("ops")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}")).andExpect(jsonPath("$.status").value("RECEIVED"));

        // спор по лоферам → возврат
        String received = mvc.perform(get("/api/orders/{n}", n).header(KEY, key))
                .andExpect(jsonPath("$.canDispute").value(true))
                .andReturn().getResponse().getContentAsString();
        Integer loaferRub = JsonPath.read(received, "$.items[0].lineRub");
        mvc.perform(post("/api/orders/{n}/disputes", n).header(KEY, key).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[0],\"reason\":\"DAMAGED\",\"comment\":\"Отклеилась подошва\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dispute.status").value("OPEN"))
                .andExpect(jsonPath("$.canDispute").value(false));

        mvc.perform(post("/api/ops/orders/{n}/complete", n).with(role("ops")))
                .andExpect(jsonPath("$.title").value("dispute_open"));

        String cases = mvc.perform(get("/api/admin/disputes").param("status", "OPEN").with(role("support")))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(cases, "$[?(@.order.number == '" + n + "')].dispute.id");
        assertThat(ids).hasSize(1);
        mvc.perform(post("/api/admin/disputes/{id}/resolve", ids.getFirst()).with(role("support"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"refund\":false}"))
                .andExpect(jsonPath("$.title").value("resolution_required"));
        mvc.perform(post("/api/admin/disputes/{id}/resolve", ids.getFirst()).with(role("support"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"refund\":true}"))
                .andExpect(jsonPath("$.dispute.status").value("REFUNDED"))
                .andExpect(jsonPath("$.dispute.refundRub").value(loaferRub))
                .andExpect(jsonPath("$.order.refundRub").value(loaferRub))
                .andExpect(jsonPath("$.order.items[0].state").value("RETURNED"));

        mvc.perform(post("/api/ops/orders/{n}/complete", n).with(role("ops"))).andExpect(jsonPath("$.status").value("COMPLETED"));

        String history = mvc.perform(get("/api/orders/{n}", n)).andReturn().getResponse().getContentAsString();
        List<String> titles = JsonPath.read(history, "$.events[*].title");
        assertThat(titles).contains("Заказ оформлен", "Оплачен", "Продавец отправил товар на хаб", "Принят на хабе в Милане",
                "Передан перевозчику", "Ждёт в пункте выдачи", "Получен", "Заказ завершён");

        mvc.perform(get("/api/admin/payouts").with(role("support"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/payouts").with(role("finance")))
                .andExpect(jsonPath("$[?(@.legalName == 'Calzoleria Verdi')]").isNotEmpty());
    }

    @Test
    void buyerCancelsPaidOrderAndGetsFullRefund() throws Exception {
        String[] order = order();
        mvc.perform(post("/api/orders/{n}/cancel", order[0])).andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/{n}/pay", order[0]).header(KEY, order[1])).andExpect(status().isOk());
        String body = mvc.perform(post("/api/orders/{n}/cancel", order[0]).header(KEY, order[1]))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andReturn().getResponse().getContentAsString();
        Integer total = JsonPath.read(body, "$.totalRub");
        Integer refund = JsonPath.read(body, "$.refundRub");
        assertThat(refund).isEqualTo(total);
    }

    @Test
    void sellerDeclineThatLeavesOnlyFoodCancelsOrder() throws Exception {
        String[] order = order();
        mvc.perform(post("/api/orders/{n}/pay", order[0]).header(KEY, order[1])).andExpect(status().isOk());
        mvc.perform(post("/api/seller/orders/{n}/decline", order[0]).with(user(DEMO_SELLER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[0],\"reason\":\"Размер закончился\"}"))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.lines[0].state").value("DECLINED"));
        mvc.perform(get("/api/orders/{n}", order[0]).header(KEY, order[1]))
                .andExpect(jsonPath("$.cancelReason").value("Остались только продукты, их не отправляем отдельно"))
                .andExpect(jsonPath("$.items[1].state").value("DECLINED"));
    }

    /** Лоферы демо-продавца + масло оператора. Возвращает номер и ключ. */
    private String[] order() throws Exception {
        String products = mvc.perform(get("/api/products")).andReturn().getResponse().getContentAsString();
        List<Integer> loafers = JsonPath.read(products, "$[?(@.title == 'Лоферы из замши, мужские')].id");
        List<Integer> oil = JsonPath.read(products, "$[?(@.title == 'Масло Extra Vergine, 0,75 л')].id");
        String cartId = JsonPath.read(mvc.perform(post("/api/carts")).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/carts/{id}/items", cartId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + loafers.getFirst() + ",\"size\":\"43\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/carts/{id}/items", cartId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + oil.getFirst() + "}")).andExpect(status().isOk());
        String body = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"cartId":"%s","buyerName":"Мария Соколова","phone":"79160000002","passport":"4500111222",
                         "inn":"770000000002","pickupPoint":"СДЭК · Москва, ул. Покровка, 12","consent":true}""".formatted(cartId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessKey").exists())
                .andExpect(jsonPath("$.items[1].state").value("SHIPPED"))
                .andReturn().getResponse().getContentAsString();
        return new String[] {JsonPath.read(body, "$.number"), JsonPath.read(body, "$.accessKey")};
    }

    private static RequestPostProcessor user(String sub) {
        return jwt().jwt(j -> j.subject(sub));
    }

    private static RequestPostProcessor role(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
