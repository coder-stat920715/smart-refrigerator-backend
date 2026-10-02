package com.smartfridge.event;

import com.smartfridge.entity.FaultSeverity;
import com.smartfridge.entity.FaultType;

import java.time.Instant;
import java.util.UUID;

public record FaultOpenedEvent(UUID faultId, UUID deviceId, FaultType type, FaultSeverity severity,
                               String message, Instant openedAt) {
}
