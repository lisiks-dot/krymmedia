package ru.pacco.orders.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Заглушка эквайринга: подтверждает любой платёж. Заменить адаптером банка перед запуском. */
@Component
class StubPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StubPaymentGateway.class);

    @Override
    public void charge(String orderNumber, long amountRub) {
        log.warn("Платёж по заказу {} на {} ₽ подтверждён заглушкой эквайринга", orderNumber, amountRub);
    }
}
