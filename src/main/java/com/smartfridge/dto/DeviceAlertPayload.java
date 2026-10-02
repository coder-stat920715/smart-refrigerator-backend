package com.smartfridge.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DeviceAlertPayload(String code, String message, String severity) {
}
