package com.smartfridge.dto;

import com.smartfridge.entity.FaultEvent;
import com.smartfridge.entity.FaultSeverity;
import com.smartfridge.entity.FaultType;

import java.time.Instant;
import java.util.UUID;

public record FaultEventResponse(
        UUID id,
        UUID deviceId,
        FaultType type,
        FaultSeverity severity,
        String message,
        Double triggerValue,
        boolean active,
        Instant openedAt,
        Instant resolvedAt) {

    public static FaultEventResponse from(FaultEvent f) {
        return new FaultEventResponse(f.getId(), f.getDeviceId(), f.getType(), f.getSeverity(), f.getMessage(),
                f.getTriggerValue(), f.isActive(), f.getOpenedAt(), f.getResolvedAt());
    }
}
