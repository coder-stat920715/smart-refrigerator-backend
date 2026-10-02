package com.smartfridge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.alerts")
public class AlertProperties {
    private double fridgeTempThreshold = 10.0;
    private long fridgeBreachMinutes = 15;
    private double freezerTempThreshold = -10.0;
    private long freezerBreachMinutes = 15;
    private long doorOpenMinutes = 5;
    private long compressorContinuousHours = 6;
    private double compressorMinTempDrop = 1.0;
}
