package com.smartfridge.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OtaStatusPayload(UUID jobId, String version, String status, String message) {
}
