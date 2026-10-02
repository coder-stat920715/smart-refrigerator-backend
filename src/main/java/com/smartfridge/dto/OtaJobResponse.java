package com.smartfridge.dto;

import com.smartfridge.entity.OtaJobStatus;
import com.smartfridge.entity.OtaUpdateJob;

import java.time.Instant;
import java.util.UUID;

public record OtaJobResponse(
        UUID id,
        UUID deviceId,
        UUID firmwareId,
        String targetVersion,
        OtaJobStatus status,
        String message,
        Instant createdAt,
        Instant updatedAt) {

    public static OtaJobResponse from(OtaUpdateJob j) {
        return new OtaJobResponse(j.getId(), j.getDeviceId(), j.getFirmwareId(), j.getTargetVersion(),
                j.getStatus(), j.getMessage(), j.getCreatedAt(), j.getUpdatedAt());
    }
}
