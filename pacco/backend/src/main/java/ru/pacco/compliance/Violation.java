package ru.pacco.compliance;

/** Нарушение правила: код правила (C-01…C-04) и понятное продавцу объяснение. */
public record Violation(String code, String message) {

    @Override
    public String toString() {
        return code + " · " + message;
    }
}
