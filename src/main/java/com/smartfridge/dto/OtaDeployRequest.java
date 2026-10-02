package com.smartfridge.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record OtaDeployRequest(@NotEmpty List<@NotNull UUID> deviceIds) {
}
