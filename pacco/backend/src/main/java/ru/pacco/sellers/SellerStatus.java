package ru.pacco.sellers;

public enum SellerStatus {
    /** Заявка подана, ждёт проверки модератором. */
    PENDING,
    /** Проверен: может публиковать товары и получать заказы. */
    ACTIVE,
    /** Заявка отклонена. */
    REJECTED
}
