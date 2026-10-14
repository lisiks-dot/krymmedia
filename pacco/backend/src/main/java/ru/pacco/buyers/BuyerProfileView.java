package ru.pacco.buyers;

public record BuyerProfileView(String name, String phone, String email, Long pickupPointId,
                               boolean notifySms, boolean notifyEmail) {

    public static BuyerProfileView empty(String email) {
        return new BuyerProfileView(null, null, email, null, true, true);
    }
}
