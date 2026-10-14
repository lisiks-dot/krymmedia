package ru.pacco.compliance;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import ru.pacco.shared.Category;

/** Что проверяем: категория, код ТН ВЭД (CN), цена за единицу и признак аэрозоля. */
public record ItemSpec(
        @NotNull Category category,
        String hsCode,
        @PositiveOrZero long priceEurCents,
        boolean aerosol) {
}
