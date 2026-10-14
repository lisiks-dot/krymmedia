package ru.pacco.compliance;

/** Событие: правила изменились, опубликованный каталог нужно перепроверить. */
public record ComplianceRulesChanged(RulesView rules) {
}
