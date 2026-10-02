package com.smartfridge.dto;

import com.smartfridge.entity.Device;
import com.smartfridge.entity.DeviceState;
import com.smartfridge.entity.DeviceStatus;

import java.time.Instant;
import java.util.UUID;

public record DeviceResponse(
        UUID id,
        String macAddress,
        String name,
        String model,
        String hardwareRev,
        String firmwareVersion,
        DeviceStatus status,
        DeviceState operationalState,
        Instant lastSeenAt,
        Instant createdAt,
        Instant updatedAt) {

    public static DeviceResponse from(Device d) {
        return new DeviceResponse(d.getId(), d.getMacAddress(), d.getName(), d.getModel(), d.getHardwareRev(),
                d.getFirmwareVersion(), d.getStatus(), d.getOperationalState(), d.getLastSeenAt(),
                d.getCreatedAt(), d.getUpdatedAt());
    }
}
