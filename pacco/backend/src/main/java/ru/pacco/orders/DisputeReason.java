package ru.pacco.orders;

public enum DisputeReason {
    NOT_AS_DESCRIBED("Не соответствует описанию"),
    DAMAGED("Повреждён"),
    WRONG_ITEM("Пришёл другой товар"),
    MISSING("Товара нет в посылке"),
    SIZE("Не подошёл размер");

    private final String label;

    DisputeReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
