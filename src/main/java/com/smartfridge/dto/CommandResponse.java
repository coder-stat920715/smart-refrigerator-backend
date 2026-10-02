package com.smartfridge.dto;

import com.smartfridge.entity.CommandType;

import java.time.Instant;
import java.util.UUID;

public record CommandResponse(
        UUID commandId,
        UUID deviceId,
        CommandType type,
        String topic,
        String protocol,
        Instant dispatchedAt) {
}
