package com.smartfridge.dto;

import jakarta.validation.constraints.Size;

/** All fields optional; only non-blank values are applied. */
public record DeviceUpdateRequest(
        @Size(max = 100) String name,
        @Size(max = 64) String model,
        @Size(max = 16) String hardwareRev) {
}
