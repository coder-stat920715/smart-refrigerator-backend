package com.smartfridge.dto;

import java.time.Instant;
import java.util.UUID;

public record TelemetryStatsResponse(
        UUID deviceId,
        Instant from,
        Instant to,
        long samples,
        Double avgFridgeTemp,
        Double minFridgeTemp,
        Double maxFridgeTemp,
        Double avgFreezerTemp,
        Double minFreezerTemp,
        Double maxFreezerTemp,
        Double compressorDutyCycle) {
}
