package ru.pacco.orders;

/** 12 статусов основного пути заказа (ТЗ, раздел «Статусы заказа») и отмена. */
public enum OrderStatus {
    CREATED("Создан"),
    PAID("Оплачен"),
    CONFIRMED("Подтверждён продавцом"),
    TO_HUB("Едет на хаб"),
    AT_HUB("Принят на хабе"),
    CONSOLIDATED("Упакован в посылку"),
    HANDED_TO_CARRIER("Передан перевозчику"),
    IN_CUSTOMS("На таможне"),
    RELEASED("Выпущен"),
    AT_PICKUP_POINT("В ПВЗ или у курьера"),
    RECEIVED("Получен"),
    COMPLETED("Завершён"),
    CANCELLED("Отменён");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isFinal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /** Следующий статус основного пути. */
    public OrderStatus next() {
        if (isFinal()) {
            throw new IllegalStateException("Статус " + this + " конечный");
        }
        return values()[ordinal() + 1];
    }
}
