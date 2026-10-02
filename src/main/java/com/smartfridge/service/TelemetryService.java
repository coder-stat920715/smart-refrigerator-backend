package com.smartfridge.service;

import com.smartfridge.dto.PageResponse;
import com.smartfridge.dto.TelemetryAggregate;
import com.smartfridge.dto.TelemetryResponse;
import com.smartfridge.dto.TelemetryStatsResponse;
import com.smartfridge.exception.BadRequestException;
import com.smartfridge.exception.ResourceNotFoundException;
import com.smartfridge.repository.DeviceRepository;
import com.smartfridge.repository.TelemetryLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Read side of telemetry plus retention. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryService {

    private final TelemetryLogRepository telemetryRepository;
    private final DeviceRepository deviceRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TelemetryResponse latest(UUID deviceId) {
        requireDevice(deviceId);
        return telemetryRepository.findFirstByDeviceIdOrderByTimeDesc(deviceId)
                .map(TelemetryResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("No telemetry recorded yet for device " + deviceId));
    }

    @Transactional(readOnly = true)
    public PageResponse<TelemetryResponse> history(UUID deviceId, Instant from, Instant to, Pageable pageable) {
        requireDevice(deviceId);
        Instant end = to != null ? to : Instant.now(clock);
        Instant start = from != null ? from : end.minus(Duration.ofHours(24));
        validateRange(start, end);
        return PageResponse.of(telemetryRepository.findByDeviceIdAndTimeBetween(deviceId, start, end, pageable),
                TelemetryResponse::from);
    }

    @Transactional(readOnly = true)
    public TelemetryStatsResponse stats(UUID deviceId, Instant from, Instant to) {
        requireDevice(deviceId);
        Instant end = to != null ? to : Instant.now(clock);
        Instant start = from != null ? from : end.minus(Duration.ofHours(24));
        validateRange(start, end);

        TelemetryAggregate a = telemetryRepository.aggregate(deviceId, start, end);
        long samples = a.samples() == null ? 0 : a.samples();
        Double duty = null;
        if (samples > 0 && a.compressorOnSamples() != null) {
            duty = (double) a.compressorOnSamples() / samples;
        }
        return new TelemetryStatsResponse(deviceId, start, end, samples,
                a.avgFridgeTemp(), a.minFridgeTemp(), a.maxFridgeTemp(),
                a.avgFreezerTemp(), a.minFreezerTemp(), a.maxFreezerTemp(), duty);
    }

    @Transactional
    public int purgeOlderThanDays(int days) {
        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(days));
        int deleted = telemetryRepository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Telemetry retention removed {} rows older than {}", deleted, cutoff);
        }
        return deleted;
    }

    private void requireDevice(UUID deviceId) {
        if (!deviceRepository.existsById(deviceId)) {
            throw new ResourceNotFoundException("Device " + deviceId + " not found");
        }
    }

    private void validateRange(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new BadRequestException("'from' must be earlier than 'to'");
        }
    }
}
