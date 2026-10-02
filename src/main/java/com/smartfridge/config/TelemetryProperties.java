package com.smartfridge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.telemetry")
public class TelemetryProperties {
    private double minTemp = -60.0;
    private double maxTemp = 60.0;
    private long spikeWindowSeconds = 30;
    private double maxSpikeCelsius = 15.0;
    private int retentionDays = 30;
}
