package ru.pacco.delivery;

public record PickupPointView(long id, String carrier, String city, String address, double lat, double lng,
                              String hours, String note, boolean fitting) {

    /** Как пункт печатается в заказе и уведомлениях. */
    public String label() {
        return carrier + " · " + city + ", " + address;
    }
}
