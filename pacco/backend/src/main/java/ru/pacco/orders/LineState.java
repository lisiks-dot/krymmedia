package ru.pacco.orders;

/** Состояние строки заказа: путь товара одного продавца от подтверждения до выдачи или возврата денег. */
public enum LineState {
    PENDING("Ждёт подтверждения продавца", true),
    CONFIRMED("Подтверждён продавцом", true),
    SHIPPED("Отправлен на хаб", true),
    ACCEPTED("Принят на хабе", true),
    DECLINED("Продавец отказал, деньги вернули", false),
    REJECTED_AT_HUB("Не прошёл проверку на хабе, деньги вернули", false),
    RETURNED("Возврат по спору", false);

    private final String label;
    private final boolean active;

    LineState(String label, boolean active) {
        this.label = label;
        this.active = active;
    }

    public String label() {
        return label;
    }

    /** Строка ещё едет к покупателю (не отменена и не возвращена). */
    public boolean active() {
        return active;
    }
}
