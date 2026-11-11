package org.example.back_end_iot.repository;

import org.example.back_end_iot.entity.ActionHistory;
import org.example.back_end_iot.entity.Device;
import org.example.back_end_iot.enums.ActionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ActionHistoryRepository
        extends JpaRepository<ActionHistory, Integer>,
        JpaSpecificationExecutor<ActionHistory> {
    boolean existsByDeviceAndStatus(Device device, ActionStatus status);

    List<ActionHistory> findByStatusAndCreatedAtBefore(ActionStatus status, LocalDateTime cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ActionHistory a where a.requestId = :requestId")
    Optional<ActionHistory> findByRequestIdForUpdate(@Param("requestId") String requestId);

    @Query("select a from ActionHistory a join fetch a.device where a.requestId = :requestId and a.user.username = :username")
    Optional<ActionHistory> findByRequestIdAndUsername(@Param("requestId") String requestId,
                                                       @Param("username") String username);
}
