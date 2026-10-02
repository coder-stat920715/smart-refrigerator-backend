package com.smartfridge.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Used when MQTT is disabled (app.mqtt.enabled=false): records outgoing messages instead of publishing. */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "false")
public class LoggingCommandExecutionStrategy implements CommandExecutionStrategy {

    private final List<String> sentMessages = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void deliver(String topic, String payload, int qos) {
        log.info("[MQTT disabled] would publish to {} (qos {}): {}", topic, qos, payload);
        sentMessages.add(topic + " " + payload);
    }

    @Override
    public String protocolName() {
        return "LOGGING";
    }

    public List<String> getSentMessages() {
        return sentMessages;
    }
}
