package ru.pacco.orders;

/** Способ оплаты; без тела запроса — карта МИР. */
public record PayRequest(PaymentMethod method) {
}
