package com.smartfridge.service;

import com.smartfridge.entity.Device;
import com.smartfridge.entity.DeviceState;
import com.smartfridge.entity.DeviceStatus;
import com.smartfridge.entity.FaultEvent;
import com.smartfridge.entity.FaultType;
import com.smartfridge.entity.TelemetryLog;
import com.smartfridge.repository.DeviceRepository;
import com.smartfridge.repository.FaultEventRepository;
import com.smartfridge.repository.TelemetryLogRepository;
import com.smartfridge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlertServiceTest extends AbstractIntegrationTest {

    private static final Instant BASE = Instant.parse("2025-01-01T10:00:00Z");

    @Autowired
    private AlertService alertService;
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
                .macAddress("AA:BB:CC:DD:EE:10")
                .name("Test fridge")
                .hardwareRev("A1")
                .firmwareVersion("1.0.0")
                .status(DeviceStatus.ONLINE)
                .operationalState(DeviceState.NORMAL_COOLING)
                .build());
    }

    private void ingest(Instant time, double fridge, boolean door, boolean compressor) {
        TelemetryLog reading = telemetryRepository.save(TelemetryLog.builder()
                .deviceId(device.getId())
                .time(time)
                .freezerTemp(-18.0)
                .fridgeTemp(fridge)
                .compressorOn(compressor)
                .doorOpen(door)
                .build());
        alertService.evaluate(reading);
    }

    private List<FaultEvent> activeFaults() {
        return faultRepository.findByDeviceIdAndActiveTrue(device.getId());
    }

    @Test
    void fridgeOverTemperatureFaultOpensAfterFifteenMinutesAndAutoResolves() {
        ingest(BASE, 12.0, false, true);
        ingest(BASE.plus(Duration.ofMinutes(10)), 12.0, false, true);
        assertThat(activeFaults()).isEmpty();

        ingest(BASE.plus(Duration.ofMinutes(16)), 12.5, false, true);
        assertThat(activeFaults()).hasSize(1);
        assertThat(activeFaults().get(0).getType()).isEqualTo(FaultType.FRIDGE_OVER_TEMPERATURE);

        Device faulty = deviceRepository.findById(device.getId()).orElseThrow();
        assertThat(faulty.getStatus()).isEqualTo(DeviceStatus.FAULT);
        assertThat(faulty.getOperationalState()).isEqualTo(DeviceState.CRITICAL_FAULT);

        ingest(BASE.plus(Duration.ofMinutes(20)), 4.0, false, true);
        assertThat(activeFaults()).isEmpty();
        Device recovered = deviceRepository.findById(device.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(DeviceStatus.ONLINE);
        assertThat(recovered.getOperationalState()).isEqualTo(DeviceState.NORMAL_COOLING);
    }

    @Test
    void normalTemperaturesNeverRaiseFaults() {
        for (int minute = 0; minute <= 30; minute += 5) {
            ingest(BASE.plus(Duration.ofMinutes(minute)), 4.0, false, true);
        }
        assertThat(activeFaults()).isEmpty();
    }

    @Test
    void shortExcursionBelowFifteenMinutesDoesNotRaiseFault() {
        ingest(BASE, 12.0, false, true);
        ingest(BASE.plus(Duration.ofMinutes(5)), 4.0, false, true);
        ingest(BASE.plus(Duration.ofMinutes(10)), 12.0, false, true);
        ingest(BASE.plus(Duration.ofMinutes(20)), 12.0, false, true);
        assertThat(activeFaults()).isEmpty();
    }

    @Test
    void doorLeftOpenRaisesWarningAndDoorAlarmState() {
        ingest(BASE, 4.0, true, true);
        ingest(BASE.plus(Duration.ofMinutes(6)), 4.0, true, true);

        assertThat(activeFaults()).hasSize(1);
        assertThat(activeFaults().get(0).getType()).isEqualTo(FaultType.DOOR_LEFT_OPEN);
        Device alarmed = deviceRepository.findById(device.getId()).orElseThrow();
        assertThat(alarmed.getOperationalState()).isEqualTo(DeviceState.DOOR_ALARM);
        assertThat(alarmed.getStatus()).isEqualTo(DeviceStatus.ONLINE);

        ingest(BASE.plus(Duration.ofMinutes(7)), 4.0, false, true);
        assertThat(activeFaults()).isEmpty();
        assertThat(deviceRepository.findById(device.getId()).orElseThrow().getOperationalState())
                .isEqualTo(DeviceState.NORMAL_COOLING);
    }

    @Test
    void compressorRunningSixHoursWithoutCoolingRaisesFault() {
        for (int hour = 0; hour <= 7; hour++) {
            ingest(BASE.plus(Duration.ofHours(hour)), 6.0, false, true);
        }
        assertThat(activeFaults()).extracting(FaultEvent::getType)
                .containsExactly(FaultType.COMPRESSOR_INEFFECTIVE);
    }

    @Test
    void compressorRunningSixHoursWhileCoolingIsHealthy() {
        for (int hour = 0; hour <= 7; hour++) {
            ingest(BASE.plus(Duration.ofHours(hour)), 9.0 - hour * 0.8, false, true);
        }
        assertThat(activeFaults()).isEmpty();
    }
}
