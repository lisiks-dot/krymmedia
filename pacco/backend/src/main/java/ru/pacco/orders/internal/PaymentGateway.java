package ru.pacco.orders.internal;

/** Эквайринг МИР и СБП. Реальную реализацию подключают после договора с банком. */
public interface PaymentGateway {

    /** Списывает сумму; бросает исключение, если платёж отклонён. */
    void charge(String orderNumber, long amountRub);
}
