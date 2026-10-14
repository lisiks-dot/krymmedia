package ru.pacco.notifications;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import ru.pacco.buyers.BuyerProfiles;
import ru.pacco.notifications.internal.Notification;
import ru.pacco.notifications.internal.NotificationRepository;
import ru.pacco.notifications.internal.Senders;
import ru.pacco.orders.OrderNotices;
import ru.pacco.orders.OrderNotices.Notice;

/**
 * Рассылка по исходящей очереди событий заказа: СМС на телефон из заказа и письмо, если указан email.
 * Каналы берутся из профиля покупателя; без аккаунта — оба. Каждое сообщение пишется в журнал.
 */
@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"));

    private final OrderNotices notices;
    private final BuyerProfiles profiles;
    private final NotificationRepository journal;
    private final Senders senders;
    private final String publicUrl;

    public NotificationDispatcher(OrderNotices notices, BuyerProfiles profiles, NotificationRepository journal, Senders senders,
                                  @Value("${pacco.public-url:http://localhost}") String publicUrl) {
        this.notices = notices;
        this.profiles = profiles;
        this.journal = journal;
        this.senders = senders;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    @Scheduled(fixedDelayString = "${pacco.notifications.interval-ms:5000}")
    public void dispatch() {
        for (Notice n : notices.pending(50)) {
            try {
                send(n);
            } catch (RuntimeException e) {
                log.warn("Уведомление по событию {} не отправлено: {}", n.eventId(), e.getMessage());
            } finally {
                notices.done(n.eventId());
            }
        }
    }

    private void send(Notice n) {
        BuyerProfiles.Channels ch = profiles.channels(n.buyerSub());
        String link = publicUrl + "/orders/" + n.number() + "?key=" + n.key();
        Instant now = Instant.now();
        if (ch.sms() && n.phone() != null && !n.phone().isBlank()) {
            String text = sms(n, link);
            Senders.Result r = senders.sms(n.phone(), text);
            journal.save(new Notification(n.orderId(), n.buyerSub(), "SMS", "+" + n.phone(), null, text, r.status(), r.provider(), r.error(), now));
        }
        if (ch.email() && n.email() != null && !n.email().isBlank()) {
            String subject = "Заказ " + n.number() + ": " + headline(n);
            Senders.Result r = senders.email(n.email(), subject, html(n, link));
            journal.save(new Notification(n.orderId(), n.buyerSub(), "EMAIL", n.email(), subject, plain(n), r.status(), r.provider(), r.error(), now));
        }
    }

    /** Заголовок события для покупателя. */
    static String headline(Notice n) {
        return switch (n.title()) {
            case "Оплачен" -> "оплачен";
            case "Передан перевозчику" -> "посылка в пути";
            case "Ждёт в пункте выдачи" -> "можно забирать";
            case "Получен" -> "получен";
            case "Заказ отменён" -> "отменён";
            default -> n.title().substring(0, 1).toLowerCase(Locale.ROOT) + n.title().substring(1);
        };
    }

    static String sms(Notice n, String link) {
        String head = "ilpacco, заказ " + n.number() + ": ";
        return switch (n.title()) {
            case "Оплачен" -> head + "оплачен " + n.totalRub() + " ₽. Продавец подтвердит его за 24 ч. " + link;
            case "Передан перевозчику" -> head + "посылка передана перевозчику" + (n.carrierTracking() != null ? ", трек " + n.carrierTracking() : "") + ". " + link;
            case "Ждёт в пункте выдачи" -> head + "ждёт вас" + (n.storageUntil() != null ? " до " + DAY.format(n.storageUntil()) : "")
                    + " в " + n.pickupPoint() + ". Код получения " + n.pickupCode() + ". " + link;
            case "Получен" -> head + "получен. Если что-то не так, откройте спор в течение 14 дней: " + link;
            default -> head + n.title().toLowerCase(Locale.forLanguageTag("ru")) + (n.note() != null ? ". " + n.note() : "") + ". " + link;
        };
    }

    private static String plain(Notice n) {
        return n.title() + (n.note() != null ? ". " + n.note() : "");
    }

    /** Письмо в стиле витрины: логотип, заголовок, детали, одна кнопка. Только инлайновые стили — так его покажут почтовые клиенты. */
    static String html(Notice n, String link) {
        String esc = HtmlUtils.htmlEscape(n.note() == null ? "" : n.note());
        StringBuilder details = new StringBuilder();
        if ("Ждёт в пункте выдачи".equals(n.title()) && n.pickupCode() != null) {
            details.append("<div style=\"background:#0b5fd6;color:#fff;border-radius:12px;padding:18px 20px;margin:16px 0\">")
                    .append("<div style=\"font-size:13px\">Код получения</div>")
                    .append("<div style=\"font-size:34px;font-weight:700;letter-spacing:4px\">")
                    .append(n.pickupCode(), 0, 3).append(' ').append(n.pickupCode().substring(3)).append("</div>")
                    .append("<div style=\"font-size:13px\">").append(HtmlUtils.htmlEscape(n.pickupPoint()))
                    .append(n.storageUntil() != null ? " · хранится до " + DAY.format(n.storageUntil()) : "").append("</div></div>");
        }
        if (n.refundRub() > 0 && n.title().contains("ерн")) {
            details.append("<p style=\"color:#12805c;font-weight:600\">Всего возвращено по заказу: ").append(n.refundRub()).append(" ₽</p>");
        }
        return """
                <!doctype html><html lang="ru"><body style="margin:0;background:#f4f5f7;font-family:Arial,Helvetica,sans-serif;color:#15171c">
                <div style="max-width:560px;margin:0 auto;padding:24px">
                  <div style="font-family:Georgia,serif;font-size:28px">ilpacco<span style="font-family:Arial;font-size:13px;color:#0b5fd6;font-weight:700">.ru</span></div>
                  <div style="background:#fff;border-radius:16px;padding:24px;margin-top:16px">
                    <div style="font-size:13px;color:#5d6270">Заказ %s</div>
                    <h1 style="font-family:Georgia,serif;font-weight:400;font-size:26px;margin:6px 0 10px">%s</h1>
                    <p style="font-size:15px;line-height:1.5;margin:0">%s</p>
                    %s
                    <a href="%s" style="display:inline-block;margin-top:18px;background:#ff6b1a;color:#1c1206;font-weight:700;text-decoration:none;padding:13px 20px;border-radius:12px">Открыть заказ</a>
                  </div>
                  <p style="font-size:12px;color:#5d6270;margin-top:16px">Вы получили это письмо, потому что оформили заказ на ilpacco.ru. Отключить письма можно в профиле.</p>
                </div></body></html>""".formatted(HtmlUtils.htmlEscape(n.number()), HtmlUtils.htmlEscape(n.title()), esc, details, HtmlUtils.htmlEscape(link));
    }
}
