package com.smartfridge.service;

import com.smartfridge.config.AlertProperties;
import com.smartfridge.entity.FaultSeverity;
import com.smartfridge.entity.FaultType;
import com.smartfridge.entity.TelemetryLog;
import com.smartfridge.repository.FaultEventRepository;
import com.smartfridge.repository.TelemetryLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Alert and fault engine. Every rule is a "sustained condition" rule evaluated purely from persisted telemetry:
 * the start of the current breach is the first reading after the last non-breaching reading.
 * This keeps the engine stateless, restart-safe and safe to run on several instances.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertProperties props;
    private final TelemetryLogRepository telemetryRepository;
    private final FaultEventRepository faultRepository;
    private final FaultService faultService;

    /** REQUIRES_NEW because this is invoked from an AFTER_COMMIT callback of the ingestion transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void evaluate(TelemetryLog current) {
        final UUID deviceId = current.getDeviceId();

        evaluateSustained(current, FaultType.FRIDGE_OVER_TEMPERATURE, FaultSeverity.CRITICAL,
                current.getFridgeTemp() > props.getFridgeTempThreshold(),
                () -> telemetryRepository.findFirstByDeviceIdAndFridgeTempLessThanEqualOrderByTimeDesc(
                        deviceId, props.getFridgeTempThreshold()),
                Duration.ofMinutes(props.getFridgeBreachMinutes()),
                (start, cur) -> true,
                sustained -> String.format(Locale.ROOT,
                        "Fridge temperature %.1f C above %.1f C for %d minutes",
                        current.getFridgeTemp(), props.getFridgeTempThreshold(), sustained.toMinutes()),
                current.getFridgeTemp());

        evaluateSustained(current, FaultType.FREEZER_OVER_TEMPERATURE, FaultSeverity.CRITICAL,
                current.getFreezerTemp() > props.getFreezerTempThreshold(),
                () -> telemetryRepository.findFirstByDeviceIdAndFreezerTempLessThanEqualOrderByTimeDesc(
                        deviceId, props.getFreezerTempThreshold()),
                Duration.ofMinutes(props.getFreezerBreachMinutes()),
                (start, cur) -> true,
                sustained -> String.format(Locale.ROOT,
                        "Freezer temperature %.1f C above %.1f C for %d minutes",
                        current.getFreezerTemp(), props.getFreezerTempThreshold(), sustained.toMinutes()),
                current.getFreezerTemp());

        evaluateSustained(current, FaultType.DOOR_LEFT_OPEN, FaultSeverity.WARNING,
                Boolean.TRUE.equals(current.getDoorOpen()),
                () -> telemetryRepository.findFirstByDeviceIdAndDoorOpenFalseOrderByTimeDesc(deviceId),
                Duration.ofMinutes(props.getDoorOpenMinutes()),
                (start, cur) -> true,
                sustained -> String.format(Locale.ROOT, "Door open for %d minutes", sustained.toMinutes()),
                null);

        evaluateSustained(current, FaultType.COMPRESSOR_INEFFECTIVE, FaultSeverity.CRITICAL,
                Boolean.TRUE.equals(current.getCompressorOn()),
                () -> telemetryRepository.findFirstByDeviceIdAndCompressorOnFalseOrderByTimeDesc(deviceId),
                Duration.ofHours(props.getCompressorContinuousHours()),
                (start, cur) -> start.getFridgeTemp() - cur.getFridgeTemp() < props.getCompressorMinTempDrop(),
                sustained -> String.format(Locale.ROOT,
                        "Compressor running for %d minutes without a fridge temperature drop of %.1f C",
                        sustained.toMinutes(), props.getCompressorMinTempDrop()),
                current.getFridgeTemp());
    }

    private void evaluateSustained(TelemetryLog current,
                                   FaultType type,
                                   FaultSeverity severity,
                                   boolean breaching,
                                   Supplier<Optional<TelemetryLog>> lastNormalLookup,
                                   Duration minDuration,
                                   BiPredicate<TelemetryLog, TelemetryLog> extraCondition,
                                   Function<Duration, String> messageFactory,
                                   Double triggerValue) {
        UUID deviceId = current.getDeviceId();

        if (!breaching) {
            faultService.resolveActive(deviceId, type, current.getTime());
            return;
        }
        if (faultRepository.findFirstByDeviceIdAndTypeAndActiveTrue(deviceId, type).isPresent()) {
            return; // already raised
        }

        Instant after = lastNormalLookup.get().map(TelemetryLog::getTime).orElse(Instant.EPOCH);
        TelemetryLog start = telemetryRepository.findFirstByDeviceIdAndTimeAfterOrderByTimeAsc(deviceId, after)
                .orElse(current);
        Duration sustained = Duration.between(start.getTime(), current.getTime());

        if (sustained.compareTo(minDuration) < 0) {
            return;
        }
        if (!extraCondition.test(start, current)) {
            return;
        }
        faultService.open(deviceId, type, severity, messageFactory.apply(sustained), triggerValue, current.getTime());
    }
}
