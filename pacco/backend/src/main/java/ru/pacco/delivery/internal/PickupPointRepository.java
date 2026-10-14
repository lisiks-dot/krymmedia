package ru.pacco.delivery.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PickupPointRepository extends JpaRepository<PickupPoint, Long> {

    List<PickupPoint> findByActiveTrueOrderByCityAscIdAsc();

    List<PickupPoint> findByActiveTrueAndCityOrderByIdAsc(String city);

    @Query("select p.city from PickupPoint p where p.active = true group by p.city order by count(p) desc, p.city")
    List<String> cities();
}
