package com.smartfridge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.firmware")
public class FirmwareProperties {
    private String storageDir = "./firmware-store";
    private String publicBaseUrl = "http://localhost:8080";
    private String signingSecret = "change-me-in-production";
}
