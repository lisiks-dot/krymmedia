package ru.pacco.orders;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Сроки жизненного цикла заказа.
 *
 * @param confirmHours      сколько часов у продавца на подтверждение оплаченного заказа
 * @param shipDays          за сколько рабочих дней продавец отправляет товар на хаб
 * @param storageDays       сколько дней посылка ждёт покупателя в ПВЗ
 * @param returnWindowDays  сколько дней после получения можно открыть спор; потом заказ завершается и продавцу платят
 */
@ConfigurationProperties(prefix = "pacco.orders")
public record OrdersProperties(int confirmHours, int shipDays, int storageDays, int returnWindowDays) {

    public OrdersProperties {
        if (confirmHours <= 0) confirmHours = 24;
        if (shipDays <= 0) shipDays = 3;
        if (storageDays <= 0) storageDays = 7;
        if (returnWindowDays <= 0) returnWindowDays = 14;
    }
}
