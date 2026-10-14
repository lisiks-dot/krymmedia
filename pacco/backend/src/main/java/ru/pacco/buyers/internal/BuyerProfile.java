package ru.pacco.buyers.internal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import ru.pacco.buyers.BuyerProfileView;

@Entity
@Table(name = "buyer_profiles")
public class BuyerProfile {

    @Id
    private String sub;
    private String name;
    private String phone;
    private String email;
    @Column(name = "pickup_point_id")
    private Long pickupPointId;
    @Column(name = "notify_sms", nullable = false)
    private boolean notifySms = true;
    @Column(name = "notify_email", nullable = false)
    private boolean notifyEmail = true;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BuyerProfile() {
    }

    public BuyerProfile(String sub) {
        this.sub = sub;
        this.updatedAt = Instant.now();
    }

    public void update(String name, String phone, String email, Long pickupPointId, boolean sms, boolean mail, Instant at) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.pickupPointId = pickupPointId;
        this.notifySms = sms;
        this.notifyEmail = mail;
        this.updatedAt = at;
    }

    public void fillBlanks(String name, String phone, String email, Long pickupPointId, Instant at) {
        if (this.name == null) this.name = name;
        if (this.phone == null) this.phone = phone;
        if (this.email == null) this.email = email;
        if (this.pickupPointId == null) this.pickupPointId = pickupPointId;
        this.updatedAt = at;
    }

    public BuyerProfileView view() {
        return new BuyerProfileView(name, phone, email, pickupPointId, notifySms, notifyEmail);
    }
}
