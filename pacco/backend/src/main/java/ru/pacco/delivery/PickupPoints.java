package ru.pacco.delivery;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.pacco.delivery.internal.PickupPoint;
import ru.pacco.delivery.internal.PickupPointRepository;

/** Пункты выдачи. Сейчас — справочник в базе; позже — синхронизация с API СДЭК, Boxberry и Почты. */
@Service
public class PickupPoints {

    private final PickupPointRepository points;

    public PickupPoints(PickupPointRepository points) {
        this.points = points;
    }

    @Transactional(readOnly = true)
    public List<PickupPointView> list(String city) {
        List<PickupPoint> all = city == null || city.isBlank()
                ? points.findByActiveTrueOrderByCityAscIdAsc()
                : points.findByActiveTrueAndCityOrderByIdAsc(city.trim());
        return all.stream().map(PickupPoint::view).toList();
    }

    @Transactional(readOnly = true)
    public List<String> cities() {
        return points.cities();
    }

    @Transactional(readOnly = true)
    public Optional<PickupPointView> get(long id) {
        return points.findById(id).filter(PickupPoint::isActive).map(PickupPoint::view);
    }
}
