package ru.pacco.compliance;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import ru.pacco.shared.Category;

/** Текущие правила комплаенса. */
public record RulesView(
        long thresholdEurCents,
        Set<Category> thresholdCategories,
        List<String> embargoPrefixes,
        List<String> alcoholPrefixes,
        Instant updatedAt) {
}
