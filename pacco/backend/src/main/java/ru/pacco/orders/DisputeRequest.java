package ru.pacco.orders;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DisputeRequest(@NotEmpty List<Integer> lines, @NotNull DisputeReason reason,
                             @Size(max = 1000) String comment) {
}
