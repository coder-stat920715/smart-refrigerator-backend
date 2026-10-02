package com.smartfridge.mqtt;

import com.smartfridge.exception.CommandDispatchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttCommandExecutionStrategy implements CommandExecutionStrategy {

    private final MqttGateway gateway;

    @Override
    public void deliver(String topic, String payload, int qos) {
        try {
            gateway.publish(topic, qos, payload);
            log.debug("Published to {} (qos {}): {}", topic, qos, payload);
        } catch (Exception ex) {
            throw new CommandDispatchException("Failed to publish command to MQTT broker: " + ex.getMessage(), ex);
        }
    }

    @Override
    public String protocolName() {
        return "MQTT-QOS1";
    }
}
