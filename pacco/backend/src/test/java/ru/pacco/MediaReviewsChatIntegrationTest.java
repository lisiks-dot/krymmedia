package ru.pacco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

/** Фото и видео товара от продавца, отзывы с подтверждённой покупкой и ответами, чат покупателя и продавца. */
@SpringBootTest
class MediaReviewsChatIntegrationTest {

    private static final String DEMO_SELLER = "11111111-1111-4111-8111-111111111111";
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    /** Заголовок MP4: размер бокса и «ftyp». */
    private static final byte[] MP4 = new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0,
            'i', 's', 'o', 'm', 'm', 'p', '4', '1'};

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }

    @Test
    void sellerUploadsPhotosAndVideo() throws Exception {
        long loafers = loafers();
        MockMultipartFile png = new MockMultipartFile("file", "a.png", "image/png", PNG);

        mvc.perform(multipart("/api/seller/products/{id}/media", loafers).file(png)).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/seller/products/{id}/media", loafers).file(png).with(jwt().jwt(j -> j.subject("stranger"))))
                .andExpect(status().is4xxClientError());
        mvc.perform(multipart("/api/seller/products/{id}/media", loafers)
                        .file(new MockMultipartFile("file", "a.exe", "image/png", "MZ-not-an-image".getBytes())).with(seller()))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.title").value("media_type"));

        Integer first = JsonPath.read(mvc.perform(multipart("/api/seller/products/{id}/media", loafers).file(png).with(seller()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.kind").value("IMAGE"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        Integer video = JsonPath.read(mvc.perform(multipart("/api/seller/products/{id}/media", loafers)
                        .file(new MockMultipartFile("file", "v.mp4", "video/mp4", MP4)).with(seller()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.kind").value("VIDEO"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        // порядок: видео первым
        mvc.perform(put("/api/seller/products/{id}/media/order", loafers).with(seller()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[%d,%d]}".formatted(video, first)))
                .andExpect(jsonPath("$[0].id").value(video));
        mvc.perform(get("/api/products/{id}", loafers))
                .andExpect(jsonPath("$.media[0].kind").value("VIDEO"))
                .andExpect(jsonPath("$.media[1].url").value("/api/products/%d/media/%d".formatted(loafers, first)));
        mvc.perform(get("/api/products/{id}/media/{m}", loafers, first))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age")));

        // удаление: продавец — своё, администратор — любое
        mvc.perform(delete("/api/seller/products/{id}/media/{m}", loafers, video).with(seller())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/products/{id}/media/{m}", loafers, first).with(role("admin"))).andExpect(status().is2xxSuccessful());
        mvc.perform(get("/api/products/{id}/media/{m}", loafers, first)).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/{id}", loafers)).andExpect(jsonPath("$.media.length()").value(0));
    }

    @Test
    void reviewsNeedReceivedPurchaseAndGetReplies() throws Exception {
        long loafers = loafers();
        String buyer = "buyer-" + UUID.randomUUID();
        String review = "{\"rating\":5,\"text\":\"Мягкая замша, размер в размер, носятся с первого дня.\"}";

        mvc.perform(get("/api/products/{id}/reviews/me", loafers).with(buyer(buyer))).andExpect(jsonPath("$.canReview").value(false));
        mvc.perform(post("/api/products/{id}/reviews", loafers).contentType(MediaType.APPLICATION_JSON).content(review))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/products/{id}/reviews", loafers).with(buyer(buyer)).contentType(MediaType.APPLICATION_JSON).content(review))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.title").value("not_purchased"));

        String n = receivedOrder(buyer, loafers);

        mvc.perform(get("/api/products/{id}/reviews/me", loafers).with(buyer(buyer)))
                .andExpect(jsonPath("$.canReview").value(true)).andExpect(jsonPath("$.orderNumber").value(n));
        mvc.perform(post("/api/products/{id}/reviews", loafers).with(buyer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":7,\"text\":\"Отлично, но коротко\"}")).andExpect(status().isUnprocessableContent());
        String created = mvc.perform(post("/api/products/{id}/reviews", loafers).with(buyer(buyer)).contentType(MediaType.APPLICATION_JSON).content(review))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author").value("Олег К."))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.size").value("44"))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(created, "$.id");
        mvc.perform(post("/api/products/{id}/reviews", loafers).with(buyer(buyer)).contentType(MediaType.APPLICATION_JSON).content(review))
                .andExpect(jsonPath("$.title").value("already_reviewed"));

        // публично — без номера заказа
        String pub = mvc.perform(get("/api/products/{id}/reviews", loafers)).andReturn().getResponse().getContentAsString();
        List<Object> orderNumbers = JsonPath.read(pub, "$.reviews[?(@.id == " + id + ")].orderNumber");
        assertThat(orderNumbers).containsOnlyNulls();
        assertThat((Integer) JsonPath.read(pub, "$.summary.count")).isGreaterThanOrEqualTo(1);

        // ответ продавца и администрации
        mvc.perform(post("/api/seller/reviews/{id}/replies", id).with(jwt().jwt(j -> j.subject("stranger"))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Grazie!\"}")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/seller/reviews").with(seller())).andExpect(jsonPath("$[?(@.id == " + id + ")]").exists());
        mvc.perform(post("/api/seller/reviews/{id}/replies", id).with(seller()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Спасибо! Рады, что подошли.\"}"))
                .andExpect(jsonPath("$.replies[0].role").value("SELLER"));
        mvc.perform(post("/api/admin/reviews/{id}/replies", id).with(role("buyer")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/reviews/{id}/replies", id).with(role("support")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Спасибо за отзыв.\"}"))
                .andExpect(jsonPath("$.replies[1].role").value("ADMIN"))
                .andExpect(jsonPath("$.replies[1].author").value("ilpacco"));

        // модерация
        mvc.perform(post("/api/admin/reviews/{id}/hide", id).with(role("moderator")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"проверка\"}")).andExpect(jsonPath("$.hidden").value(true));
        String hidden = mvc.perform(get("/api/products/{id}/reviews", loafers)).andReturn().getResponse().getContentAsString();
        assertThat((List<?>) JsonPath.read(hidden, "$.reviews[?(@.id == " + id + ")]")).isEmpty();
        mvc.perform(get("/api/admin/reviews").param("hidden", "true").with(role("moderator")))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].orderNumber").value(n));
        mvc.perform(post("/api/admin/reviews/{id}/show", id).with(role("moderator"))).andExpect(jsonPath("$.hidden").value(false));
    }

    @Test
    void buyerAndSellerChatWithStaffView() throws Exception {
        long loafers = loafers();
        String buyer = "buyer-" + UUID.randomUUID();

        mvc.perform(post("/api/me/chats").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":%d,\"text\":\"Привет\"}".formatted(loafers))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/chats").with(buyer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":%d,\"orderNumber\":\"PC-1\",\"text\":\"Привет\"}".formatted(loafers)))
                .andExpect(jsonPath("$.title").value("order_not_yours"));

        String started = mvc.perform(post("/api/me/chats").with(buyer(buyer)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"text\":\"Подойдут на узкую стопу? Мой номер +7 916 111-22-33\"}".formatted(loafers)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversation.sellerName").value("Calzoleria Verdi"))
                .andExpect(jsonPath("$.messages[0].masked").value(true))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(started, "$.conversation.id");
        assertThat((String) JsonPath.read(started, "$.messages[0].text")).doesNotContain("916").contains("[контакт скрыт]");

        // повторное обращение по тому же товару — тот же диалог
        mvc.perform(post("/api/me/chats").with(buyer(buyer)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"text\":\"И ещё: есть 45?\"}".formatted(loafers)))
                .andExpect(jsonPath("$.conversation.id").value(id))
                .andExpect(jsonPath("$.messages.length()").value(2));

        // чужой покупатель не видит
        mvc.perform(get("/api/me/chats/{id}", id).with(buyer("someone-else"))).andExpect(status().isNotFound());

        // продавец: непрочитано, ответ
        mvc.perform(get("/api/seller/chats").with(seller()))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].unread").value(2));
        mvc.perform(get("/api/seller/chats/{id}", id).with(seller())).andExpect(jsonPath("$.messages.length()").value(2));
        mvc.perform(get("/api/seller/chats").with(seller())).andExpect(jsonPath("$[?(@.id == " + id + ")].unread").value(0));
        mvc.perform(post("/api/seller/chats/{id}/messages", id).with(seller()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Колодка средняя, 45 есть. Пишите: verdi@example.it\"}"))
                .andExpect(jsonPath("$.messages[2].role").value("SELLER"))
                .andExpect(jsonPath("$.messages[2].masked").value(true));
        mvc.perform(post("/api/seller/chats/{id}/messages", id).with(seller()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"  \"}")).andExpect(jsonPath("$.title").value("text_required"));

        mvc.perform(get("/api/me/chats").with(buyer(buyer)))
                .andExpect(jsonPath("$[0].id").value(id)).andExpect(jsonPath("$[0].unread").value(1));

        // администратор видит переписку и отвечает от поддержки
        mvc.perform(get("/api/admin/chats").with(role("buyer"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/chats").param("q", "узкую").with(role("support")))
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").exists());
        mvc.perform(get("/api/admin/chats/{id}", id).with(role("admin")))
                .andExpect(jsonPath("$.messages.length()").value(3))
                .andExpect(jsonPath("$.conversation.buyerName").value("Олег К."));
        mvc.perform(post("/api/admin/chats/{id}/messages", id).with(role("support")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Подключилась поддержка ilpacco.\"}"))
                .andExpect(jsonPath("$.messages[3].role").value("ADMIN"))
                .andExpect(jsonPath("$.messages[3].author").value("Поддержка ilpacco"));
        mvc.perform(get("/api/me/chats/{id}", id).with(buyer(buyer))).andExpect(jsonPath("$.messages.length()").value(4));
        mvc.perform(get("/api/me/chats").with(buyer(buyer))).andExpect(jsonPath("$[0].unread").value(0));
    }

    private long loafers() throws Exception {
        String products = mvc.perform(get("/api/products")).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(products, "$[?(@.title == 'Лоферы из замши, мужские')].id");
        return ids.getFirst();
    }

    /** Заказ лоферов, доведённый до получения покупателем. */
    private String receivedOrder(String sub, long loafers) throws Exception {
        String points = mvc.perform(get("/api/pickup-points").param("city", "Москва")).andReturn().getResponse().getContentAsString();
        Integer pointId = JsonPath.read(points, "$[0].id");
        String cartId = JsonPath.read(mvc.perform(post("/api/carts")).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/carts/{id}/items", cartId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + loafers + ",\"size\":\"44\"}")).andExpect(status().isOk());
        String order = mvc.perform(post("/api/orders").with(buyer(sub)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"cartId":"%s","buyerName":"Олег Карпов","phone":"79161112233","passport":"4500999888",
                         "inn":"770000000009","pickupPointId":%d,"email":"oleg@example.ru","consent":true}""".formatted(cartId, pointId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String n = JsonPath.read(order, "$.number");
        mvc.perform(post("/api/orders/{n}/pay", n).with(buyer(sub))).andExpect(jsonPath("$.status").value("PAID"));
        mvc.perform(post("/api/seller/orders/{n}/confirm", n).with(seller())).andExpect(status().isOk());
        mvc.perform(post("/api/seller/orders/{n}/ship", n).with(seller()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tracking\":\"BRT-2\"}")).andExpect(jsonPath("$.status").value("TO_HUB"));
        mvc.perform(post("/api/ops/orders/{n}/accept", n).with(role("ops")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"lineNo\":0,\"ok\":true}]}")).andExpect(jsonPath("$.status").value("AT_HUB"));
        mvc.perform(post("/api/ops/orders/{n}/pack", n).with(role("ops")).contentType(MediaType.APPLICATION_JSON).content("{\"weightGrams\":1100}"));
        mvc.perform(post("/api/ops/orders/{n}/handover", n).with(role("ops")).contentType(MediaType.APPLICATION_JSON).content("{\"tracking\":\"CDEK-7\"}"));
        for (String step : List.of("customs", "release", "arrive")) {
            mvc.perform(post("/api/ops/orders/{n}/" + step, n).with(role("ops"))).andExpect(status().isOk());
        }
        String code = JsonPath.read(mvc.perform(get("/api/orders/{n}", n).with(buyer(sub))).andReturn().getResponse().getContentAsString(), "$.pickupCode");
        mvc.perform(post("/api/ops/orders/{n}/issue", n).with(role("ops")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}")).andExpect(jsonPath("$.status").value("RECEIVED"));
        return n;
    }

    private static RequestPostProcessor buyer(String sub) {
        return jwt().jwt(j -> j.subject(sub).claim("email", "oleg@example.ru").claim("given_name", "Олег").claim("family_name", "Карпов"));
    }

    private static RequestPostProcessor seller() {
        return jwt().jwt(j -> j.subject(DEMO_SELLER));
    }

    private static RequestPostProcessor role(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
