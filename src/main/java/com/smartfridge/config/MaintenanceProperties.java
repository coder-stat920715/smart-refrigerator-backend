package com.smartfridge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.maintenance")
public class MaintenanceProperties {
    private long heartbeatTimeoutSeconds = 90;
    private long otaJobTimeoutMinutes = 60;
}
