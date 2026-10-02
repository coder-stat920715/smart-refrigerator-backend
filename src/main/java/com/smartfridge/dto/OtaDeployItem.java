package com.smartfridge.dto;

import java.util.UUID;

public record OtaDeployItem(UUID deviceId, boolean accepted, UUID jobId, String reason) {
}
