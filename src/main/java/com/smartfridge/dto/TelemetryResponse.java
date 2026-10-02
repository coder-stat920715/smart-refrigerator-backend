package com.smartfridge.dto;

import com.smartfridge.entity.TelemetryLog;

import java.time.Instant;
import java.util.UUID;

public record TelemetryResponse(
        Long id,
        UUID deviceId,
        Instant time,
        Double freezerTemp,
        Double fridgeTemp,
        Boolean compressorOn,
        Boolean doorOpen) {

    public static TelemetryResponse from(TelemetryLog t) {
        return new TelemetryResponse(t.getId(), t.getDeviceId(), t.getTime(), t.getFreezerTemp(),
                t.getFridgeTemp(), t.getCompressorOn(), t.getDoorOpen());
    }
}
