package com.smartfridge.mqtt;

import com.smartfridge.entity.Device;
import com.smartfridge.entity.DeviceState;
import com.smartfridge.entity.DeviceStatus;
import com.smartfridge.entity.FaultEvent;
import com.smartfridge.entity.FaultType;
import com.smartfridge.repository.DeviceRepository;
import com.smartfridge.repository.FaultEventRepository;
import com.smartfridge.repository.TelemetryLogRepository;
import com.smartfridge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MqttMessageRouterTest extends AbstractIntegrationTest {

    @Autowired
    private MqttMessageRouter router;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private TelemetryLogRepository telemetryRepository;
    @Autowired
    private FaultEventRepository faultRepository;

    private Device device;

    @BeforeEach
    void createDevice() {
        device = deviceRepository.save(Device.builder()
                .macAddress("AA:BB:CC:DD:EE:20")
                .name("Router test fridge")
                .hardwareRev("A1")
                .firmwareVersion("1.0.0")
                .status(DeviceStatus.OFFLINE)
                .operationalState(DeviceState.NORMAL_COOLING)
                .build());
    }

    private String topic(String channel) {
        return "appliance/" + device.getId() + "/" + channel;
    }

    @Test
    void telemetryIsPersistedAndMarksDeviceOnline() {
        router.route(topic("telemetry"),
                "{\"freezerTemp\": -18.5, \"fridgeTemp\": 4.0, \"compressorOn\": true, \"doorOpen\": false}");

        assertThat(telemetryRepository.countByDeviceId(device.getId())).isEqualTo(1);
        Device updated = deviceRepository.findById(device.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(DeviceStatus.ONLINE);
        assertThat(updated.getLastSeenAt()).isNotNull();
    }

    @Test
    void outOfRangeReadingIsFilteredButStillCountsAsHeartbeat() {
        router.route(topic("telemetry"),
                "{\"freezerTemp\": -18.5, \"fridgeTemp\": 999.0, \"compressorOn\": true, \"doorOpen\": false}");

        assertThat(telemetryRepository.countByDeviceId(device.getId())).isZero();
        assertThat(deviceRepository.findById(device.getId()).orElseThrow().getStatus())
                .isEqualTo(DeviceStatus.ONLINE);
    }

    @Test
    void sudden_spikeIsDropped() {
        router.route(topic("telemetry"),
                "{\"freezerTemp\": -18.0, \"fridgeTemp\": 4.0, \"compressorOn\": true, \"doorOpen\": false}");
        router.route(topic("telemetry"),
                "{\"freezerTemp\": -18.0, \"fridgeTemp\": 40.0, \"compressorOn\": true, \"doorOpen\": false}");

        assertThat(telemetryRepository.countByDeviceId(device.getId())).isEqualTo(1);
    }

    @Test
    void malformedPayloadAndUnknownTopicsDoNotThrow() {
        router.route(topic("telemetry"), "this is not json");
        router.route("something/else", "{}");
        router.route("appliance/not-a-uuid/telemetry", "{}");
        router.route("appliance/" + UUID.randomUUID() + "/telemetry",
                "{\"freezerTemp\": -18.0, \"fridgeTemp\": 4.0, \"compressorOn\": true, \"doorOpen\": false}");

        assertThat(telemetryRepository.count()).isZero();
    }

    @Test
    void deviceAlertCreatesFaultEvent() {
        router.route(topic("alerts"), "{\"code\":\"E21\",\"message\":\"Evaporator fan stalled\",\"severity\":\"CRITICAL\"}");

        List<FaultEvent> faults = faultRepository.findByDeviceIdAndActiveTrue(device.getId());
        assertThat(faults).hasSize(1);
        assertThat(faults.get(0).getType()).isEqualTo(FaultType.DEVICE_REPORTED);
        assertThat(faults.get(0).getMessage()).contains("E21");
        assertThat(deviceRepository.findById(device.getId()).orElseThrow().getStatus())
                .isEqualTo(DeviceStatus.FAULT);
    }

    @Test
    void deviceReportedStateUpdatesOperationalState() {
        router.route(topic("state"), "{\"state\":\"DEFROST_CYCLE\"}");

        assertThat(deviceRepository.findById(device.getId()).orElseThrow().getOperationalState())
                .isEqualTo(DeviceState.DEFROST_CYCLE);
    }
}
