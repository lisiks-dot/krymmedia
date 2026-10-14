package ru.pacco.catalog.internal;

public enum ProductStatus {
    /** В продаже. */
    PUBLISHED,
    /** Снят правилами комплаенса; вернётся в продажу, если правила это позволят. */
    BLOCKED
}
