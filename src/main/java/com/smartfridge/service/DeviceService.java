package com.smartfridge.service;

import com.smartfridge.dto.DeviceCreateRequest;
import com.smartfridge.dto.DeviceResponse;
import com.smartfridge.dto.DeviceStatusResponse;
import com.smartfridge.dto.DeviceUpdateRequest;
import com.smartfridge.dto.PageResponse;
import com.smartfridge.entity.Device;
import com.smartfridge.entity.DeviceState;
import com.smartfridge.entity.DeviceStatus;
import com.smartfridge.entity.FaultSeverity;
import com.smartfridge.entity.FaultType;
import com.smartfridge.exception.DuplicateResourceException;
import com.smartfridge.exception.ResourceNotFoundException;
import com.smartfridge.repository.DeviceRepository;
import com.smartfridge.repository.FaultEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final FaultEventRepository faultEventRepository;
    private final DeviceStateMachine stateMachine;
    private final Clock clock;

    // ------------------------------------------------------------------ CRUD

    @Transactional
    public DeviceResponse register(DeviceCreateRequest request) {
        String mac = request.macAddress().trim().toUpperCase(Locale.ROOT);
        if (deviceRepository.existsByMacAddress(mac)) {
            throw new DuplicateResourceException("A device with MAC address " + mac + " is already registered");
        }
        Device device = Device.builder()
                .macAddress(mac)
                .name(request.name().trim())
                .model(request.model() == null ? null : request.model().trim())
                .hardwareRev(request.hardwareRev().trim())
                .firmwareVersion(request.firmwareVersion().trim())
                .status(DeviceStatus.OFFLINE)
                .operationalState(DeviceState.NORMAL_COOLING)
                .build();
        Device saved = deviceRepository.saveAndFlush(device);
        log.info("Registered device {} ({})", saved.getId(), saved.getMacAddress());
        return DeviceResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Device getEntity(UUID id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Device " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public DeviceResponse get(UUID id) {
        return DeviceResponse.from(getEntity(id));
    }

    @Transactional(readOnly = true)
    public DeviceResponse getByMac(String mac) {
        String normalized = mac.trim().toUpperCase(Locale.ROOT);
        return deviceRepository.findByMacAddress(normalized)
                .map(DeviceResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Device with MAC " + normalized + " not found"));
    }

    @Transactional(readOnly = true)
    public PageResponse<DeviceResponse> list(DeviceStatus status, Pageable pageable) {
        var page = status == null ? deviceRepository.findAll(pageable) : deviceRepository.findByStatus(status, pageable);
        return PageResponse.of(page, DeviceResponse::from);
    }

    @Transactional
    public DeviceResponse update(UUID id, DeviceUpdateRequest request) {
        Device device = getEntity(id);
        if (hasText(request.name())) {
            device.setName(request.name().trim());
        }
        if (hasText(request.model())) {
            device.setModel(request.model().trim());
        }
        if (hasText(request.hardwareRev())) {
            device.setHardwareRev(request.hardwareRev().trim());
        }
        return DeviceResponse.from(deviceRepository.save(device));
    }

    @Transactional
    public void delete(UUID id) {
        Device device = getEntity(id);
        deviceRepository.delete(device);
        log.info("Deleted device {}", id);
    }

    @Transactional(readOnly = true)
    public DeviceStatusResponse getStatus(UUID id) {
        Device d = getEntity(id);
        long activeFaults = faultEventRepository.countByDeviceIdAndActiveTrue(id);
        return new DeviceStatusResponse(d.getId(), d.getStatus(), d.getOperationalState(),
                d.getStatus() != DeviceStatus.OFFLINE, d.getLastSeenAt(), d.getFirmwareVersion(), activeFaults);
    }

    // ----------------------------------------------------- liveness and state

    /** Every telemetry message doubles as a heartbeat. */
    @Transactional
    public void recordHeartbeat(Device device, Instant seenAt) {
        device.setLastSeenAt(seenAt);
        if (device.getStatus() == DeviceStatus.OFFLINE) {
            boolean critical = faultEventRepository.existsByDeviceIdAndSeverityAndActiveTrue(
                    device.getId(), FaultSeverity.CRITICAL);
            device.setStatus(critical ? DeviceStatus.FAULT : DeviceStatus.ONLINE);
            log.info("Device {} is back online", device.getId());
        }
        deviceRepository.save(device);
    }

    @Transactional
    public int markStaleDevicesOffline(Duration timeout) {
        Instant now = Instant.now(clock);
        int updated = deviceRepository.markStale(DeviceStatus.OFFLINE,
                List.of(DeviceStatus.ONLINE, DeviceStatus.FAULT), now.minus(timeout), now);
        if (updated > 0) {
            log.warn("Marked {} device(s) OFFLINE (no heartbeat for {} s)", updated, timeout.toSeconds());
        }
        return updated;
    }

    /** The MCU is the source of truth for its own operational state. */
    @Transactional
    public void reportState(UUID deviceId, String stateName) {
        Optional<Device> device = deviceRepository.findById(deviceId);
        if (device.isEmpty() || stateName == null) {
            log.warn("Ignoring state report for device {} (unknown device or empty state)", deviceId);
            return;
        }
        try {
            DeviceState state = DeviceState.valueOf(stateName.trim().toUpperCase(Locale.ROOT));
            device.get().setOperationalState(state);
            deviceRepository.save(device.get());
        } catch (IllegalArgumentException ex) {
            log.warn("Device {} reported unknown state '{}'", deviceId, stateName);
        }
    }

    @Transactional
    public void markDefrostStarted(UUID deviceId) {
        applyEvent(getEntity(deviceId), StateEvent.DEFROST_STARTED);
    }

    @Transactional
    public void onFaultOpened(UUID deviceId, FaultType type, FaultSeverity severity) {
        Device device = getEntity(deviceId);
        if (type == FaultType.DOOR_LEFT_OPEN) {
            applyEvent(device, StateEvent.DOOR_ALARM_RAISED);
            return;
        }
        if (severity == FaultSeverity.CRITICAL) {
            device.setOperationalState(stateMachine.transition(device.getOperationalState(),
                    StateEvent.CRITICAL_FAULT_RAISED));
            if (device.getStatus() != DeviceStatus.UPDATING) {
                device.setStatus(DeviceStatus.FAULT);
            }
            deviceRepository.save(device);
        }
    }

    @Transactional
    public void onFaultResolved(UUID deviceId, FaultType type) {
        Device device = getEntity(deviceId);
        if (type == FaultType.DOOR_LEFT_OPEN) {
            applyEvent(device, StateEvent.DOOR_ALARM_CLEARED);
            return;
        }
        boolean criticalRemaining = faultEventRepository.existsByDeviceIdAndSeverityAndActiveTrue(
                deviceId, FaultSeverity.CRITICAL);
        if (!criticalRemaining) {
            device.setOperationalState(stateMachine.transition(device.getOperationalState(),
                    StateEvent.FAULTS_CLEARED));
            if (device.getStatus() == DeviceStatus.FAULT) {
                device.setStatus(DeviceStatus.ONLINE);
            }
            deviceRepository.save(device);
        }
    }

    // ----------------------------------------------------------------- OTA

    @Transactional
    public void markUpdating(UUID deviceId) {
        Device device = getEntity(deviceId);
        device.setStatus(DeviceStatus.UPDATING);
        deviceRepository.save(device);
    }

    @Transactional
    public void completeFirmwareUpdate(UUID deviceId, String version) {
        Device device = getEntity(deviceId);
        device.setFirmwareVersion(version);
        deviceRepository.save(device);
        restoreStatusAfterUpdate(deviceId);
    }

    @Transactional
    public void restoreStatusAfterUpdate(UUID deviceId) {
        Device device = getEntity(deviceId);
        boolean critical = faultEventRepository.existsByDeviceIdAndSeverityAndActiveTrue(
                deviceId, FaultSeverity.CRITICAL);
        device.setStatus(critical ? DeviceStatus.FAULT : DeviceStatus.ONLINE);
        deviceRepository.save(device);
    }

    // ------------------------------------------------------------- helpers

    private void applyEvent(Device device, StateEvent event) {
        device.setOperationalState(stateMachine.transition(device.getOperationalState(), event));
        deviceRepository.save(device);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
