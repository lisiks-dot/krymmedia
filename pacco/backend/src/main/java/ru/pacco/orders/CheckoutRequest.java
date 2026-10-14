package ru.pacco.orders;

import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import ru.pacco.pricing.DeliveryMode;

/**
 * Оформление заказа. Паспорт и ИНН нужны таможне для посылок физлицам;
 * в MVP хранится только маска паспорта, полные данные уходят таможенному представителю.
 */
public record CheckoutRequest(
        @NotNull UUID cartId,
        DeliveryMode mode,
        @NotBlank String buyerName,
        @NotBlank String phone,
        @NotBlank String passport,
        @NotBlank String inn,
        @NotBlank String pickupPoint,
        @AssertTrue(message = "нужно согласие на обработку персональных данных") boolean consent) {
}
