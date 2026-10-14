package ru.pacco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import ru.pacco.notifications.NotificationDispatcher;

/** Кабинет покупателя, пункты выдачи, фото с хаба и из спора, реестр выплат с отметкой «выплачено», уведомления. */
@SpringBootTest
class AccountPhotosPayoutsIntegrationTest {

    private static final String DEMO_SELLER = "11111111-1111-4111-8111-111111111111";
    private static final String KEY = "X-Order-Key";
    /** Минимальный валидный PNG 1×1. */
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired
    WebApplicationContext context;

    @Autowired
    NotificationDispatcher dispatcher;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }

    @Test
    void buyerAccountPickupMapPhotosPayoutsAndNotifications() throws Exception {
        String buyer = "buyer-" + UUID.randomUUID();

        // пункты выдачи
        String moscow = mvc.perform(get("/api/pickup-points").param("city", "Москва"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Integer pointId = JsonPath.read(moscow, "$[0].id");
        mvc.perform(get("/api/pickup-points/cities")).andExpect(jsonPath("$[0]").value("Москва"));

        // профиль
        mvc.perform(get("/api/me/profile")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/profile").with(buyer(buyer))).andExpect(jsonPath("$.notifySms").value(true));

        // заказ под аккаунтом, ПВЗ по id, с email
        String order = checkout(buyer, pointId);
        String n = JsonPath.read(order, "$.number");
        String key = JsonPath.read(order, "$.accessKey");
        assertThat((String) JsonPath.read(order, "$.pickupPoint")).startsWith("СДЭК · Москва");
        assertThat((Boolean) JsonPath.read(order, "$.linkedToAccount")).isTrue();
        mvc.perform(get("/api/me/profile").with(buyer(buyer)))
                .andExpect(jsonPath("$.name").value("Олег Карпов"))
                .andExpect(jsonPath("$.pickupPointId").value(pointId));

        // владелец по аккаунту, без ключа
        mvc.perform(post("/api/orders/{n}/pay", n).with(buyer(buyer))).andExpect(jsonPath("$.status").value("PAID"));
        mvc.perform(get("/api/orders/{n}", n).with(buyer("someone-else"))).andExpect(jsonPath("$.canCancel").value(false));
        mvc.perform(get("/api/me/orders").with(buyer(buyer))).andExpect(jsonPath("$[0].number").value(n));

        // анонимный заказ привязывается к аккаунту по ключу
        String anon = checkout(null, pointId);
        String n2 = JsonPath.read(anon, "$.number");
        String k2 = JsonPath.read(anon, "$.accessKey");
        mvc.perform(post("/api/me/orders/claim").with(buyer(buyer)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orders\":[{\"number\":\"%s\",\"key\":\"%s\"},{\"number\":\"%s\",\"key\":\"%s\"}]}"
                                .formatted(n2, k2, n, UUID.randomUUID())))
                .andExpect(jsonPath("$.length()").value(2));

        // продавец → хаб с фото
        mvc.perform(post("/api/seller/orders/{n}/confirm", n).with(seller())).andExpect(status().isOk());
        mvc.perform(post("/api/seller/orders/{n}/ship", n).with(seller()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tracking\":\"BRT-1\"}")).andExpect(jsonPath("$.status").value("TO_HUB"));
        MockMultipartFile photo = new MockMultipartFile("file", "loafers.png", "image/png", PNG);
        mvc.perform(multipart("/api/ops/orders/{n}/photos", n).file(new MockMultipartFile("file", "x.txt", "image/png", "hello".getBytes()))
                .with(role("ops"))).andExpect(jsonPath("$.title").value("photo_type"));
        String uploaded = mvc.perform(multipart("/api/ops/orders/{n}/photos", n).file(photo).param("lineNo", "0").with(role("ops")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Integer photoId = JsonPath.read(uploaded, "$.id");
        mvc.perform(get("/api/orders/{n}/photos/{id}", n, photoId)).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/{n}/photos/{id}", n, photoId).header(KEY, key))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get("/api/orders/{n}", n).with(buyer(buyer))).andExpect(jsonPath("$.photos[0].kind").value("HUB"));
        mvc.perform(get("/api/orders/{n}", n)).andExpect(jsonPath("$.photos.length()").value(0));

        String accept = "{\"lines\":[{\"lineNo\":0,\"ok\":true},{\"lineNo\":1,\"ok\":true}]}";
        mvc.perform(post("/api/ops/orders/{n}/accept", n).with(role("ops")).contentType(MediaType.APPLICATION_JSON).content(accept))
                .andExpect(jsonPath("$.status").value("AT_HUB"));
        mvc.perform(post("/api/ops/orders/{n}/pack", n).with(role("ops")).contentType(MediaType.APPLICATION_JSON).content("{\"weightGrams\":1300}"));
        mvc.perform(post("/api/ops/orders/{n}/handover", n).with(role("ops")).contentType(MediaType.APPLICATION_JSON).content("{\"tracking\":\"CDEK-9\"}"));
        for (String step : List.of("customs", "release", "arrive")) {
            mvc.perform(post("/api/ops/orders/{n}/" + step, n).with(role("ops"))).andExpect(status().isOk());
        }
        String code = JsonPath.read(mvc.perform(get("/api/orders/{n}", n).with(buyer(buyer))).andReturn().getResponse().getContentAsString(), "$.pickupCode");

        // уведомления: СМС с кодом и письмо (провайдеры в тестах не настроены — журнал LOGGED)
        dispatcher.dispatch();
        String journal = mvc.perform(get("/api/admin/notifications").param("order", n).with(role("support")))
                .andReturn().getResponse().getContentAsString();
        List<String> sms = JsonPath.read(journal, "$[?(@.channel == 'SMS')].body");
        assertThat(sms).anyMatch(b -> b.contains("Код получения " + code));
        assertThat(sms).anyMatch(b -> b.contains("оплачен"));
        List<String> mails = JsonPath.read(journal, "$[?(@.channel == 'EMAIL')].recipient");
        assertThat(mails).contains("oleg@example.ru");
        mvc.perform(get("/api/me/notifications").with(buyer(buyer))).andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(2)));

        // выдача, спор с фото покупателя
        mvc.perform(post("/api/ops/orders/{n}/issue", n).with(role("ops")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}")).andExpect(jsonPath("$.status").value("RECEIVED"));
        mvc.perform(multipart("/api/orders/{n}/photos", n).file(photo).with(buyer(buyer))).andExpect(jsonPath("$.title").value("no_open_dispute"));
        mvc.perform(post("/api/orders/{n}/disputes", n).with(buyer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[1],\"reason\":\"DAMAGED\"}")).andExpect(status().isCreated());
        mvc.perform(multipart("/api/orders/{n}/photos", n).file(photo).param("lineNo", "1").header(KEY, key))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.kind").value("DISPUTE"));
        String cases = mvc.perform(get("/api/admin/disputes").param("status", "OPEN").with(role("support"))).andReturn().getResponse().getContentAsString();
        List<Integer> dispute = JsonPath.read(cases, "$[?(@.order.number == '" + n + "')].dispute.id");
        List<String> kinds = JsonPath.read(cases, "$[?(@.order.number == '" + n + "')].order.photos[*].kind");
        assertThat(kinds).containsExactlyInAnyOrder("HUB", "DISPUTE");
        mvc.perform(post("/api/admin/disputes/{id}/resolve", dispute.getFirst()).with(role("support"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"refund\":true}")).andExpect(status().isOk());
        mvc.perform(post("/api/ops/orders/{n}/complete", n).with(role("ops"))).andExpect(jsonPath("$.status").value("COMPLETED"));

        // реестр выплат: формирование, повтор без новых строк, отметка «выплачено»
        String batch = mvc.perform(post("/api/admin/payouts/batches").with(role("finance")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        List<Integer> mine = JsonPath.read(batch, "$[?(@.sellerName == 'Calzoleria Verdi')].id");
        assertThat(mine).hasSize(1);
        mvc.perform(post("/api/admin/payouts/batches").with(role("finance"))).andExpect(jsonPath("$.title").value("nothing_to_pay"));
        mvc.perform(get("/api/admin/payouts/batches/{id}", mine.getFirst()).with(role("finance")))
                .andExpect(jsonPath("$.seller.iban").exists())
                .andExpect(jsonPath("$.lines[?(@.orderNumber == '" + n + "')].commissionEurCents").value(2844));
        mvc.perform(post("/api/admin/payouts/batches/{id}/paid", mine.getFirst()).with(role("finance"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"bankRef\":\"\"}")).andExpect(jsonPath("$.title").value("bank_ref_required"));
        mvc.perform(post("/api/admin/payouts/batches/{id}/paid", mine.getFirst()).with(role("finance"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"bankRef\":\"SWIFT 0042\"}")).andExpect(jsonPath("$.status").value("PAID"));
        mvc.perform(get("/api/seller/payouts/history").with(seller()))
                .andExpect(jsonPath("$[0].status").value("PAID"))
                .andExpect(jsonPath("$[0].bankRef").value("SWIFT 0042"));
        mvc.perform(get("/api/seller/payouts").with(seller())).andExpect(jsonPath("$.payableNetEurCents").value(0));

        // настройки уведомлений
        mvc.perform(put("/api/me/profile").with(buyer(buyer)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Олег Карпов\",\"phone\":\"79161112233\",\"email\":\"oleg@example.ru\",\"pickupPointId\":%d,\"notifySms\":false,\"notifyEmail\":true}".formatted(pointId)))
                .andExpect(jsonPath("$.notifySms").value(false));
    }

    private String checkout(String sub, Integer pointId) throws Exception {
        String products = mvc.perform(get("/api/products")).andReturn().getResponse().getContentAsString();
        List<Integer> loafers = JsonPath.read(products, "$[?(@.title == 'Лоферы из замши, мужские')].id");
        List<Integer> oil = JsonPath.read(products, "$[?(@.title == 'Масло Extra Vergine, 0,75 л')].id");
        String cartId = JsonPath.read(mvc.perform(post("/api/carts")).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/carts/{id}/items", cartId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + loafers.getFirst() + ",\"size\":\"44\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/carts/{id}/items", cartId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + oil.getFirst() + "}")).andExpect(status().isOk());
        var req = post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"cartId":"%s","buyerName":"Олег Карпов","phone":"79161112233","passport":"4500999888",
                 "inn":"770000000009","pickupPointId":%d,"email":"oleg@example.ru","consent":true}""".formatted(cartId, pointId));
        if (sub != null) {
            req = req.with(buyer(sub));
        }
        return mvc.perform(req).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private static RequestPostProcessor buyer(String sub) {
        return jwt().jwt(j -> j.subject(sub).claim("email", "oleg@example.ru"));
    }

    private static RequestPostProcessor seller() {
        return jwt().jwt(j -> j.subject(DEMO_SELLER));
    }

    private static RequestPostProcessor role(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
