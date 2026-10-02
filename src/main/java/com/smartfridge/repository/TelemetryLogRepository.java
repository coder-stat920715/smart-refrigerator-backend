package com.smartfridge.repository;

import com.smartfridge.dto.TelemetryAggregate;
import com.smartfridge.entity.TelemetryLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TelemetryLogRepository extends JpaRepository<TelemetryLog, Long> {

    Page<TelemetryLog> findByDeviceIdAndTimeBetween(UUID deviceId, Instant from, Instant to, Pageable pageable);

    long countByDeviceId(UUID deviceId);

    Optional<TelemetryLog> findFirstByDeviceIdOrderByTimeDesc(UUID deviceId);

    Optional<TelemetryLog> findFirstByDeviceIdAndFridgeTempLessThanEqualOrderByTimeDesc(UUID deviceId, Double threshold);

    Optional<TelemetryLog> findFirstByDeviceIdAndFreezerTempLessThanEqualOrderByTimeDesc(UUID deviceId, Double threshold);

    Optional<TelemetryLog> findFirstByDeviceIdAndDoorOpenFalseOrderByTimeDesc(UUID deviceId);

    Optional<TelemetryLog> findFirstByDeviceIdAndCompressorOnFalseOrderByTimeDesc(UUID deviceId);

    Optional<TelemetryLog> findFirstByDeviceIdAndTimeAfterOrderByTimeAsc(UUID deviceId, Instant after);

    @Query("select new com.smartfridge.dto.TelemetryAggregate("
            + "count(t), avg(t.fridgeTemp), min(t.fridgeTemp), max(t.fridgeTemp), "
            + "avg(t.freezerTemp), min(t.freezerTemp), max(t.freezerTemp), "
            + "sum(case when t.compressorOn = true then 1L else 0L end)) "
            + "from TelemetryLog t where t.deviceId = :deviceId and t.time between :from and :to")
    TelemetryAggregate aggregate(@Param("deviceId") UUID deviceId,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to);

    @Modifying
    @Query("delete from TelemetryLog t where t.time < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
