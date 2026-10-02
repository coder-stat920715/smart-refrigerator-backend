package com.smartfridge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.mqtt")
public class MqttProperties {
    private boolean enabled = true;
    private String brokerUrl = "tcp://localhost:1883";
    private String username;
    private String password;
    private String clientIdPrefix = "smart-fridge-backend";
    private int qos = 1;
    private boolean cleanSession = true;
    private int keepAliveSeconds = 30;
    private int connectionTimeoutSeconds = 10;
    private long completionTimeoutMs = 5000;
    /** When non-blank, inbound topics are subscribed as $share/{group}/{filter} for horizontal scaling. */
    private String sharedSubscriptionGroup = "";
}
