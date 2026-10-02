package com.smartfridge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Registration request for a new smart refrigerator MCU")
public record DeviceCreateRequest(
        @Schema(example = "AA:BB:CC:DD:EE:01")
        @NotBlank
        @Pattern(regexp = "^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$", message = "must be a MAC address like AA:BB:CC:DD:EE:FF")
        String macAddress,

        @Schema(example = "Kitchen Fridge")
        @NotBlank @Size(max = 100)
        String name,

        @Schema(example = "FX-200")
        @Size(max = 64)
        String model,

        @Schema(example = "A2")
        @NotBlank @Size(max = 16)
        String hardwareRev,

        @Schema(example = "1.0.0")
        @NotBlank @Size(max = 32)
        String firmwareVersion) {
}
