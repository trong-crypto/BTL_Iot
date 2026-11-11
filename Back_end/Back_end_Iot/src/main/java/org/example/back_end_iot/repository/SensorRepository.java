package org.example.back_end_iot.repository;

import org.example.back_end_iot.entity.Sensor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SensorRepository extends JpaRepository<Sensor, Integer> {
    boolean existsByName(String name);

    Optional<Sensor> findBySensorKey(String sensorKey);
}
