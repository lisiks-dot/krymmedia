package ru.pacco.chat;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.pacco.catalog.ProductCatalog;
import ru.pacco.chat.internal.Conversation;
import ru.pacco.chat.internal.ConversationRepository;
import ru.pacco.chat.internal.Message;
import ru.pacco.chat.internal.MessageRepository;
import ru.pacco.orders.Purchases;
import ru.pacco.sellers.SellerDirectory;
import ru.pacco.shared.BusinessException;
import ru.pacco.shared.ProductSnapshot;

/**
 * Чат покупателя и продавца по товару. Если товар площадки (без продавца-партнёра) — отвечает поддержка ilpacco.
 * Сотрудники видят все диалоги и могут написать от имени поддержки. Контакты в сообщениях скрываются.
 */
@Service
public class ChatService {

    public static final String BUYER = "BUYER";
    public static final String SELLER = "SELLER";
    public static final String ADMIN = "ADMIN";

    public record ConversationView(long id, String buyerName, Long sellerId, String sellerName, Long productId,
                                   String productTitle, String orderNumber, Instant lastMessageAt, String lastMessage,
                                   int unread, Instant createdAt) {
    }

    public record MessageView(long id, String role, String author, String text, boolean masked, Instant createdAt) {
    }

    public record Thread(ConversationView conversation, List<MessageView> messages) {
    }

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ProductCatalog catalog;
    private final SellerDirectory sellers;
    private final Purchases purchases;

    public ChatService(ConversationRepository conversations, MessageRepository messages, ProductCatalog catalog,
                       SellerDirectory sellers, Purchases purchases) {
        this.conversations = conversations;
        this.messages = messages;
        this.catalog = catalog;
        this.sellers = sellers;
        this.purchases = purchases;
    }

    /** Покупатель пишет по товару: находим или открываем диалог с его продавцом. */
    @Transactional
    public Thread start(String sub, String buyerName, long productId, String orderNumber, String text) {
        ProductSnapshot p = catalog.find(productId).orElseThrow(() -> BusinessException.notFound("Товар"));
        String order = orderNumber == null || orderNumber.isBlank() ? null : orderNumber.trim();
        if (order != null && !purchases.owns(sub, order, productId)) {
            throw BusinessException.unprocessable("order_not_yours", "Этот заказ не найден в вашем кабинете");
        }
        Conversation c = (p.sellerId() == null
                ? conversations.findFirstByBuyerSubAndSellerIdIsNullAndProductId(sub, productId)
                : conversations.findFirstByBuyerSubAndSellerIdAndProductId(sub, p.sellerId(), productId))
                .orElseGet(() -> conversations.save(new Conversation(sub, buyerName, p.sellerId(),
                        p.sellerId() == null ? "Поддержка ilpacco" : p.sellerName(), productId, p.title(), order)));
        c.linkOrder(order);
        post(c, BUYER, buyerName, text);
        return thread(c, BUYER);
    }

    @Transactional
    public Thread buyerPost(String sub, long id, String name, String text) {
        Conversation c = buyerConversation(sub, id);
        post(c, BUYER, name, text);
        return thread(c, BUYER);
    }

    @Transactional
    public Thread buyerThread(String sub, long id) {
        return thread(buyerConversation(sub, id), BUYER);
    }

    @Transactional(readOnly = true)
    public List<ConversationView> buyerList(String sub) {
        return conversations.findByBuyerSubOrderByLastMessageAtDesc(sub).stream().map(c -> view(c, BUYER)).toList();
    }

    @Transactional(readOnly = true)
    public List<ConversationView> sellerList(long sellerId) {
        return conversations.findBySellerIdOrderByLastMessageAtDesc(sellerId).stream().map(c -> view(c, SELLER)).toList();
    }

    @Transactional
    public Thread sellerThread(long sellerId, long id) {
        return thread(sellerConversation(sellerId, id), SELLER);
    }

    @Transactional
    public Thread sellerPost(long sellerId, long id, String text) {
        Conversation c = sellerConversation(sellerId, id);
        post(c, SELLER, sellers.get(sellerId).legalName(), text);
        return thread(c, SELLER);
    }

    @Transactional(readOnly = true)
    public List<ConversationView> staffList(String q) {
        String needle = q == null ? "" : q.trim().toLowerCase();
        return conversations.findAllByOrderByLastMessageAtDesc(PageRequest.of(0, 300)).stream()
                .filter(c -> needle.isEmpty() || (c.getBuyerName() + " " + c.getSellerName() + " " + c.getProductTitle() + " "
                        + c.getOrderNumber() + " " + c.getLastMessage()).toLowerCase().contains(needle))
                .map(c -> view(c, ADMIN)).toList();
    }

    @Transactional
    public Thread staffThread(long id) {
        return thread(require(id), ADMIN);
    }

    @Transactional
    public Thread staffPost(long id, String text) {
        Conversation c = require(id);
        post(c, ADMIN, "Поддержка ilpacco", text);
        return thread(c, ADMIN);
    }

    /** Число диалогов с непрочитанными — для значков в меню. */
    @Transactional(readOnly = true)
    public long unreadBuyer(String sub) {
        return conversations.findByBuyerSubOrderByLastMessageAtDesc(sub).stream().filter(c -> c.getBuyerUnread() > 0).count();
    }

    private void post(Conversation c, String role, String name, String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) throw BusinessException.unprocessable("text_required", "Напишите сообщение");
        if (t.length() > 4000) throw BusinessException.unprocessable("text_long", "Сообщение — до 4000 символов");
        // сотрудники пишут как есть; у покупателя и продавца контакты скрываем
        ContactFilter.Result r = ADMIN.equals(role) ? new ContactFilter.Result(t, false) : ContactFilter.apply(t);
        messages.save(new Message(c.getId(), role, name, r.text(), r.masked()));
        c.posted(role, r.text(), Instant.now());
    }

    private Thread thread(Conversation c, String reader) {
        c.readBy(reader);
        return new Thread(view(c, reader), messages.findByConversationIdOrderByIdAsc(c.getId()).stream()
                .map(m -> new MessageView(m.getId(), m.getAuthorRole(), m.getAuthorName(), m.getText(), m.isMasked(), m.getCreatedAt()))
                .toList());
    }

    private Conversation require(long id) {
        return conversations.findById(id).orElseThrow(() -> BusinessException.notFound("Диалог"));
    }

    private Conversation buyerConversation(String sub, long id) {
        Conversation c = require(id);
        if (!c.getBuyerSub().equals(sub)) throw BusinessException.notFound("Диалог");
        return c;
    }

    private Conversation sellerConversation(long sellerId, long id) {
        Conversation c = require(id);
        if (c.getSellerId() == null || c.getSellerId() != sellerId) throw BusinessException.notFound("Диалог");
        return c;
    }

    private static ConversationView view(Conversation c, String reader) {
        int unread = switch (reader) {
            case BUYER -> c.getBuyerUnread();
            case SELLER -> c.getSellerUnread();
            default -> c.getStaffUnread();
        };
        return new ConversationView(c.getId(), c.getBuyerName(), c.getSellerId(), c.getSellerName(), c.getProductId(),
                c.getProductTitle(), c.getOrderNumber(), c.getLastMessageAt(), c.getLastMessage(), unread, c.getCreatedAt());
    }
}
