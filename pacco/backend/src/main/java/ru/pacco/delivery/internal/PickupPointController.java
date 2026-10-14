package ru.pacco.delivery.internal;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.pacco.delivery.PickupPointView;
import ru.pacco.delivery.PickupPoints;

@RestController
class PickupPointController {

    private final PickupPoints points;

    PickupPointController(PickupPoints points) {
        this.points = points;
    }

    @GetMapping("/api/pickup-points")
    List<PickupPointView> list(@RequestParam(required = false) String city) {
        return points.list(city);
    }

    @GetMapping("/api/pickup-points/cities")
    List<String> cities() {
        return points.cities();
    }
}
