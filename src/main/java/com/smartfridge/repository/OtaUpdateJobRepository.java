package com.smartfridge.repository;

import com.smartfridge.entity.OtaJobStatus;
import com.smartfridge.entity.OtaUpdateJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OtaUpdateJobRepository extends JpaRepository<OtaUpdateJob, UUID> {

    boolean existsByDeviceIdAndStatusIn(UUID deviceId, Collection<OtaJobStatus> statuses);

    boolean existsByFirmwareIdAndStatusIn(UUID firmwareId, Collection<OtaJobStatus> statuses);

    Optional<OtaUpdateJob> findFirstByDeviceIdAndStatusInOrderByCreatedAtDesc(UUID deviceId,
                                                                               Collection<OtaJobStatus> statuses);

    List<OtaUpdateJob> findByStatusInAndUpdatedAtBefore(Collection<OtaJobStatus> statuses, Instant cutoff);

    Page<OtaUpdateJob> findByDeviceIdAndStatus(UUID deviceId, OtaJobStatus status, Pageable pageable);

    Page<OtaUpdateJob> findByDeviceId(UUID deviceId, Pageable pageable);

    Page<OtaUpdateJob> findByStatus(OtaJobStatus status, Pageable pageable);
}
