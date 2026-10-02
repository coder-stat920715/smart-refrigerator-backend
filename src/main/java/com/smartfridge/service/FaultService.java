package com.smartfridge.service;

import com.smartfridge.dto.DeviceAlertPayload;
import com.smartfridge.dto.FaultEventResponse;
import com.smartfridge.dto.PageResponse;
import com.smartfridge.entity.FaultEvent;
import com.smartfridge.entity.FaultSeverity;
import com.smartfridge.entity.FaultType;
import com.smartfridge.event.FaultOpenedEvent;
import com.smartfridge.exception.ResourceNotFoundException;
import com.smartfridge.repository.DeviceRepository;
import com.smartfridge.repository.FaultEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaultService {

    private static final int MAX_MESSAGE_LENGTH = 500;

    private final FaultEventRepository faultRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceService deviceService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public FaultEvent open(UUID deviceId, FaultType type, FaultSeverity severity, String message,
                           Double triggerValue, Instant at) {
        FaultEvent fault = faultRepository.save(FaultEvent.builder()
                .deviceId(deviceId)
                .type(type)
                .severity(severity)
                .message(truncate(message))
                .triggerValue(triggerValue)
                .active(true)
                .openedAt(at)
                .build());
        deviceService.onFaultOpened(deviceId, type, severity);
        eventPublisher.publishEvent(new FaultOpenedEvent(fault.getId(), deviceId, type, severity,
                fault.getMessage(), at));
        return fault;
    }

    /** Auto-resolution used by the alert engine when the triggering condition clears. */
    @Transactional
    public void resolveActive(UUID deviceId, FaultType type, Instant at) {
        List<FaultEvent> active = faultRepository.findByDeviceIdAndTypeAndActiveTrue(deviceId, type);
        if (active.isEmpty()) {
            return;
        }
        for (FaultEvent fault : active) {
            fault.setActive(false);
            fault.setResolvedAt(at);
        }
        faultRepository.saveAll(active);
        deviceService.onFaultResolved(deviceId, type);
        log.info("Resolved {} fault(s) of type {} for device {}", active.size(), type, deviceId);
    }

    @Transactional
    public FaultEventResponse resolve(UUID faultId) {
        FaultEvent fault = faultRepository.findById(faultId)
                .orElseThrow(() -> new ResourceNotFoundException("Fault " + faultId + " not found"));
        if (fault.isActive()) {
            fault.setActive(false);
            fault.setResolvedAt(Instant.now(clock));
            faultRepository.saveAndFlush(fault);
            deviceService.onFaultResolved(fault.getDeviceId(), fault.getType());
        }
        return FaultEventResponse.from(fault);
    }

    @Transactional(readOnly = true)
    public FaultEventResponse get(UUID faultId) {
        return faultRepository.findById(faultId)
                .map(FaultEventResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Fault " + faultId + " not found"));
    }

    @Transactional(readOnly = true)
    public PageResponse<FaultEventResponse> list(UUID deviceId, Boolean active, Pageable pageable) {
        Page<FaultEvent> page;
        if (deviceId != null && active != null) {
            page = faultRepository.findByDeviceIdAndActive(deviceId, active, pageable);
        } else if (deviceId != null) {
            page = faultRepository.findByDeviceId(deviceId, pageable);
        } else if (active != null) {
            page = faultRepository.findByActive(active, pageable);
        } else {
            page = faultRepository.findAll(pageable);
        }
        return PageResponse.of(page, FaultEventResponse::from);
    }

    /** Alerts raised by the MCU itself (e.g. sensor failure codes). */
    @Transactional
    public void recordDeviceAlert(UUID deviceId, DeviceAlertPayload payload) {
        if (!deviceRepository.existsById(deviceId)) {
            log.warn("Dropping alert for unknown device {}", deviceId);
            return;
        }
        String code = payload.code() == null || payload.code().isBlank() ? "UNKNOWN" : payload.code().trim();
        String text = payload.message() == null ? "" : payload.message().trim();
        FaultSeverity severity = parseSeverity(payload.severity());
        open(deviceId, FaultType.DEVICE_REPORTED, severity, "[" + code + "] " + text, null, Instant.now(clock));
    }

    private FaultSeverity parseSeverity(String value) {
        if (value == null) {
            return FaultSeverity.WARNING;
        }
        try {
            return FaultSeverity.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return FaultSeverity.WARNING;
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return "";
        }
        return message.length() <= MAX_MESSAGE_LENGTH ? message : message.substring(0, MAX_MESSAGE_LENGTH);
    }
}
