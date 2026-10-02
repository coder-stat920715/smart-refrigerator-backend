package com.smartfridge.mqtt;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** MQTT topic contract: appliance/{deviceId}/{channel}. */
public final class Topics {

    public static final String ROOT = "appliance";
    public static final String TELEMETRY = "telemetry";
    public static final String ALERTS = "alerts";
    public static final String STATE = "state";
    public static final String OTA_STATUS = "ota/status";

    private Topics() {
    }

    public record TopicInfo(UUID deviceId, String channel) {
    }

    public static List<String> inboundFilters() {
        return List.of(
                ROOT + "/+/" + TELEMETRY,
                ROOT + "/+/" + ALERTS,
                ROOT + "/+/" + STATE,
                ROOT + "/+/" + OTA_STATUS);
    }

    public static String commands(UUID deviceId) {
        return ROOT + "/" + deviceId + "/commands";
    }

    public static String otaCommand(UUID deviceId) {
        return ROOT + "/" + deviceId + "/ota/command";
    }

    public static Optional<TopicInfo> parse(String topic) {
        if (topic == null) {
            return Optional.empty();
        }
        String[] parts = topic.split("/");
        if (parts.length < 3 || !ROOT.equals(parts[0])) {
            return Optional.empty();
        }
        try {
            UUID deviceId = UUID.fromString(parts[1]);
            String channel = String.join("/", Arrays.copyOfRange(parts, 2, parts.length));
            return Optional.of(new TopicInfo(deviceId, channel));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
