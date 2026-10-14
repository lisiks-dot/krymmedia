package ru.pacco.sellers;

import java.time.Instant;

public record SellerView(
        long id,
        String legalName,
        String partitaIva,
        String iban,
        String region,
        String contactEmail,
        SellerStatus status,
        String rejectReason,
        Instant createdAt) {
}
