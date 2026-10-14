package ru.pacco.compliance;

import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import ru.pacco.shared.Category;

/** Изменение правил администратором. */
public record RulesUpdate(
        @Positive long thresholdEurCents,
        @NotNull Set<Category> thresholdCategories,
        @NotNull List<String> embargoPrefixes,
        @NotNull List<String> alcoholPrefixes) {
}
