package ru.pacco.chat;

import java.util.regex.Pattern;

/**
 * Скрывает телефоны, email и ссылки на мессенджеры: переписка и сделка остаются на площадке —
 * так ilpacco может помочь в споре и защитить покупателя от предоплаты «мимо кассы».
 */
public final class ContactFilter {

    private static final String HIDDEN = "[контакт скрыт]";
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    private static final Pattern PHONE = Pattern.compile("(?<![\\w/])\\+?\\d[\\d\\s().-]{8,}\\d(?![\\w/])");
    private static final Pattern MESSENGER = Pattern.compile("(?i)(t\\.me|wa\\.me|whatsapp\\.com|telegram\\.me|instagram\\.com|vk\\.com)/\\S*|@[a-z0-9_]{5,}");

    private ContactFilter() {
    }

    public record Result(String text, boolean masked) {
    }

    public static Result apply(String text) {
        String out = EMAIL.matcher(text).replaceAll(HIDDEN);
        out = MESSENGER.matcher(out).replaceAll(HIDDEN);
        out = PHONE.matcher(out).replaceAll(m -> m.group().replaceAll("\\D", "").length() >= 10 ? HIDDEN : m.group());
        return new Result(out, !out.equals(text));
    }
}
