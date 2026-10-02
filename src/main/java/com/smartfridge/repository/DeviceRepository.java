package com.smartfridge.repository;

import com.smartfridge.entity.Device;
import com.smartfridge.entity.DeviceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    boolean existsByMacAddress(String macAddress);

    Optional<Device> findByMacAddress(String macAddress);

    Page<Device> findByStatus(DeviceStatus status, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Device d set d.status = :newStatus, d.updatedAt = :now "
            + "where d.status in :statuses and d.lastSeenAt < :cutoff")
    int markStale(@Param("newStatus") DeviceStatus newStatus,
                  @Param("statuses") Collection<DeviceStatus> statuses,
                  @Param("cutoff") Instant cutoff,
                  @Param("now") Instant now);
}
