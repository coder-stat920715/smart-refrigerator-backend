package com.smartfridge.event;

import com.smartfridge.entity.TelemetryLog;

public record TelemetryReceivedEvent(TelemetryLog reading) {
}
