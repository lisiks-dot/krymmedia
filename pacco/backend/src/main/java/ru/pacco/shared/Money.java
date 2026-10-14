package ru.pacco.shared;

import java.math.BigDecimal;

/** Форматирование денежных сумм для сообщений пользователю. */
public final class Money {

    private Money() {
    }

    /** 34050 → «340,5». */
    public static String eur(long cents) {
        return BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
