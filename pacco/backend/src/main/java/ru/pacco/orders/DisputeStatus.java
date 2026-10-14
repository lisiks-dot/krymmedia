package ru.pacco.orders;

public enum DisputeStatus {
    OPEN("На рассмотрении"),
    REFUNDED("Деньги вернули"),
    REJECTED("Отклонён");

    private final String label;

    DisputeStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
