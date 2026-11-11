package org.example.back_end_iot.repository;

import org.example.back_end_iot.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Integer> {
    boolean existsByDeviceKey(String deviceKey);

    Optional<Device> findByDeviceKey(String deviceKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Device d where d.id = :id")
    Optional<Device> findByIdForUpdate(@Param("id") Integer id);
}
