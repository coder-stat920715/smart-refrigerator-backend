package com.smartfridge.dto;

import com.smartfridge.entity.DeviceState;
import com.smartfridge.entity.DeviceStatus;

import java.time.Instant;
import java.util.UUID;

public record DeviceStatusResponse(
        UUID deviceId,
        DeviceStatus status,
        DeviceState operationalState,
        boolean online,
        Instant lastSeenAt,
        String firmwareVersion,
        long activeFaultCount) {
}
