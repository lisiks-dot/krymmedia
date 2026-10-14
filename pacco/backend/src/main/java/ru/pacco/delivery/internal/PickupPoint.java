package ru.pacco.delivery.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import ru.pacco.delivery.PickupPointView;

@Entity
@Table(name = "pickup_points")
public class PickupPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String carrier;
    private String city;
    private String address;
    private double lat;
    private double lng;
    private String hours;
    private String note;
    private boolean fitting;
    private boolean active;

    protected PickupPoint() {
    }

    public boolean isActive() {
        return active;
    }

    public PickupPointView view() {
        return new PickupPointView(id, carrier, city, address, lat, lng, hours, note, fitting);
    }
}
