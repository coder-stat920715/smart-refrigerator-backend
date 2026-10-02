package com.smartfridge.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Inbound MQTT payload: {"freezerTemp": -18.5, "fridgeTemp": 4.0, "compressorOn": true, "doorOpen": false}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelemetryPayload(
        Double freezerTemp,
        Double fridgeTemp,
        Boolean compressorOn,
        Boolean doorOpen) {
}
