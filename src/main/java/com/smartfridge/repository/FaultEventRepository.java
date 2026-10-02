package com.smartfridge.repository;

import com.smartfridge.entity.FaultEvent;
import com.smartfridge.entity.FaultSeverity;
import com.smartfridge.entity.FaultType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FaultEventRepository extends JpaRepository<FaultEvent, UUID> {

    Optional<FaultEvent> findFirstByDeviceIdAndTypeAndActiveTrue(UUID deviceId, FaultType type);

    List<FaultEvent> findByDeviceIdAndTypeAndActiveTrue(UUID deviceId, FaultType type);

    List<FaultEvent> findByDeviceIdAndActiveTrue(UUID deviceId);

    long countByDeviceIdAndActiveTrue(UUID deviceId);

    boolean existsByDeviceIdAndSeverityAndActiveTrue(UUID deviceId, FaultSeverity severity);

    Page<FaultEvent> findByDeviceIdAndActive(UUID deviceId, boolean active, Pageable pageable);

    Page<FaultEvent> findByDeviceId(UUID deviceId, Pageable pageable);

    Page<FaultEvent> findByActive(boolean active, Pageable pageable);
}
