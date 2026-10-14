package ru.pacco.notifications.internal;

import java.net.URI;
import java.util.Map;

import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Провайдеры доставки. СМС — SMS.ru (нужен SMS_RU_API_ID), почта — любой SMTP (spring.mail.host).
 * Если провайдер не настроен, сообщение не теряется: оно попадает в журнал со статусом LOGGED.
 */
@Component
public class Senders {

    private static final Logger log = LoggerFactory.getLogger(Senders.class);

    public record Result(String status, String provider, String error) {
    }

    private final ObjectProvider<JavaMailSender> mail;
    private final RestClient http = RestClient.create();
    private final String smsRuApiId;
    private final String smsSender;
    private final String from;

    public Senders(ObjectProvider<JavaMailSender> mail,
                   @Value("${pacco.notifications.sms-ru-api-id:}") String smsRuApiId,
                   @Value("${pacco.notifications.sms-sender:}") String smsSender,
                   @Value("${pacco.notifications.mail-from:ilpacco.ru <no-reply@ilpacco.ru>}") String from) {
        this.mail = mail;
        this.smsRuApiId = smsRuApiId;
        this.smsSender = smsSender;
        this.from = from;
    }

    public Result sms(String phone, String text) {
        if (smsRuApiId == null || smsRuApiId.isBlank()) {
            log.info("СМС на {} (провайдер не настроен): {}", phone, text);
            return new Result("LOGGED", "log", null);
        }
        try {
            UriComponentsBuilder uri = UriComponentsBuilder.fromUriString("https://sms.ru/sms/send")
                    .queryParam("api_id", smsRuApiId).queryParam("to", phone).queryParam("msg", text).queryParam("json", 1);
            if (smsSender != null && !smsSender.isBlank()) uri.queryParam("from", smsSender);
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = http.post().uri(URI.create(uri.encode().toUriString())).retrieve().body(Map.class);
            boolean ok = resp != null && "OK".equals(resp.get("status"));
            return ok ? new Result("SENT", "sms.ru", null) : new Result("FAILED", "sms.ru", String.valueOf(resp));
        } catch (RuntimeException e) {
            return new Result("FAILED", "sms.ru", e.getMessage());
        }
    }

    public Result email(String to, String subject, String html) {
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null) {
            log.info("Письмо на {} (SMTP не настроен): {}", to, subject);
            return new Result("LOGGED", "log", null);
        }
        try {
            MimeMessage m = sender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(m, false, "UTF-8");
            h.setFrom(from);
            h.setTo(to);
            h.setSubject(subject);
            h.setText(html, true);
            sender.send(m);
            return new Result("SENT", "smtp", null);
        } catch (Exception e) {
            return new Result("FAILED", "smtp", e.getMessage());
        }
    }
}
