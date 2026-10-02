package com.smartfridge.service;

import com.smartfridge.config.TelemetryProperties;
import com.smartfridge.dto.TelemetryPayload;
import com.smartfridge.entity.Device;
import com.smartfridge.entity.TelemetryLog;
import com.smartfridge.event.TelemetryEventPublisher;
import com.smartfridge.repository.DeviceRepository;
import com.smartfridge.repository.TelemetryLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Write side of telemetry: validate, filter noise, persist, update heartbeat, notify observers. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryIngestionService {

    private final DeviceRepository deviceRepository;
    private final TelemetryLogRepository telemetryRepository;
    private final DeviceService deviceService;
    private final TelemetryEventPublisher eventPublisher;
    private final TelemetryProperties properties;
    private final Clock clock;

    @Transactional
    public Optional<TelemetryLog> ingest(UUID deviceId, TelemetryPayload payload) {
        Optional<Device> deviceOpt = deviceRepository.findById(deviceId);
        if (deviceOpt.isEmpty()) {
            log.warn("Dropping telemetry for unknown device {}", deviceId);
            return Optional.empty();
        }
        Instant now = Instant.now(clock);

        // A message from the device proves it is alive, even if the reading itself is rejected.
        deviceService.recordHeartbeat(deviceOpt.get(), now);

        if (!isValid(payload)) {
            log.warn("Dropping invalid/out-of-range telemetry from device {}: {}", deviceId, payload);
            return Optional.empty();
        }
        if (isSpike(deviceId, payload, now)) {
            log.warn("Dropping spike reading from device {}: {}", deviceId, payload);
            return Optional.empty();
        }

        TelemetryLog saved = telemetryRepository.save(TelemetryLog.builder()
                .deviceId(deviceId)
                .time(now)
                .freezerTemp(payload.freezerTemp())
                .fridgeTemp(payload.fridgeTemp())
                .compressorOn(payload.compressorOn())
                .doorOpen(payload.doorOpen())
                .build());

        eventPublisher.publish(saved);
        return Optional.of(saved);
    }

    private boolean isValid(TelemetryPayload p) {
        if (p == null || p.freezerTemp() == null || p.fridgeTemp() == null
                || p.compressorOn() == null || p.doorOpen() == null) {
            return false;
        }
        return inRange(p.freezerTemp()) && inRange(p.fridgeTemp());
    }

    private boolean inRange(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value)
                && value >= properties.getMinTemp() && value <= properties.getMaxTemp();
    }

    /** Rejects physically implausible jumps relative to the previous stored reading. */
    private boolean isSpike(UUID deviceId, TelemetryPayload p, Instant now) {
        Optional<TelemetryLog> previous = telemetryRepository.findFirstByDeviceIdOrderByTimeDesc(deviceId);
        if (previous.isEmpty()) {
            return false;
        }
        TelemetryLog prev = previous.get();
        Duration age = Duration.between(prev.getTime(), now);
        if (age.compareTo(Duration.ofSeconds(properties.getSpikeWindowSeconds())) > 0) {
            return false;
        }
        double max = properties.getMaxSpikeCelsius();
        return Math.abs(p.fridgeTemp() - prev.getFridgeTemp()) > max
                || Math.abs(p.freezerTemp() - prev.getFreezerTemp()) > max;
    }
}
