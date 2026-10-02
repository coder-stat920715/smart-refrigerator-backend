package com.smartfridge.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfridge.dto.DeviceAlertPayload;
import com.smartfridge.dto.DeviceStatePayload;
import com.smartfridge.dto.OtaStatusPayload;
import com.smartfridge.dto.TelemetryPayload;
import com.smartfridge.service.DeviceService;
import com.smartfridge.service.FaultService;
import com.smartfridge.service.OtaService;
import com.smartfridge.service.TelemetryIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Routes inbound MQTT messages to the owning service. Never throws: bad messages are logged and dropped. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttMessageRouter {

    private final ObjectMapper objectMapper;
    private final TelemetryIngestionService ingestionService;
    private final FaultService faultService;
    private final DeviceService deviceService;
    private final OtaService otaService;

    public void route(String topic, String payload) {
        try {
            Topics.TopicInfo info = Topics.parse(topic).orElse(null);
            if (info == null) {
                log.warn("Ignoring message on unrecognised topic '{}'", topic);
                return;
            }
            switch (info.channel()) {
                case Topics.TELEMETRY -> ingestionService.ingest(info.deviceId(),
                        objectMapper.readValue(payload, TelemetryPayload.class));
                case Topics.ALERTS -> faultService.recordDeviceAlert(info.deviceId(),
                        objectMapper.readValue(payload, DeviceAlertPayload.class));
                case Topics.STATE -> {
                    DeviceStatePayload state = objectMapper.readValue(payload, DeviceStatePayload.class);
                    deviceService.reportState(info.deviceId(), state.state());
                }
                case Topics.OTA_STATUS -> otaService.handleStatusReport(info.deviceId(),
                        objectMapper.readValue(payload, OtaStatusPayload.class));
                default -> log.warn("Ignoring message on unsupported channel '{}' (topic {})", info.channel(), topic);
            }
        } catch (Exception ex) {
            log.error("Failed to process MQTT message on topic '{}': {}", topic, ex.getMessage());
            log.debug("Offending payload: {}", payload, ex);
        }
    }
}
