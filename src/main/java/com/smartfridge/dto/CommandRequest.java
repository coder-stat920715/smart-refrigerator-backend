package com.smartfridge.dto;

import com.smartfridge.entity.CommandType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Remote command for a refrigerator")
public record CommandRequest(
        @NotNull CommandType type,

        @Schema(description = "Required-at-least-one for SET_TEMPERATURE_TARGET (0..10 C)")
        @DecimalMin("0.0") @DecimalMax("10.0")
        Double fridgeTargetTemp,

        @Schema(description = "Required-at-least-one for SET_TEMPERATURE_TARGET (-30..-10 C)")
        @DecimalMin("-30.0") @DecimalMax("-10.0")
        Double freezerTargetTemp) {
}
