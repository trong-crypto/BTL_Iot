package org.example.back_end_iot.repository;

import org.example.back_end_iot.entity.SensorData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface SensorDataRepository
        extends JpaRepository<SensorData, Integer>,
        JpaSpecificationExecutor<SensorData> {
    Optional<SensorData> findFirstBySensor_SensorKeyOrderByCreatedAtDescIdDesc(String sensorKey);
    List<SensorData> findBySensor_SensorKey(String sensorKey, Pageable pageable);
}
