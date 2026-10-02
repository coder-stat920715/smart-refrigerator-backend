package com.smartfridge.mqtt;

/**
 * Strategy pattern: encapsulates how a command reaches a device
 * (MQTT QoS 1 publish, REST-to-MQTT bridge, logging-only for tests, ...).
 */
public interface CommandExecutionStrategy {

    /** Delivers the payload; throws CommandDispatchException when delivery fails. */
    void deliver(String topic, String payload, int qos);

    String protocolName();
}
