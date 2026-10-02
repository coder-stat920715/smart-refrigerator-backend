package com.smartfridge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfridge.config.MaintenanceProperties;
import com.smartfridge.dto.OtaDeployItem;
import com.smartfridge.dto.OtaDeployRequest;
import com.smartfridge.dto.OtaDeployResponse;
import com.smartfridge.dto.OtaJobResponse;
import com.smartfridge.dto.OtaStatusPayload;
import com.smartfridge.dto.PageResponse;
import com.smartfridge.entity.Device;
import com.smartfridge.entity.DeviceStatus;
import com.smartfridge.entity.FirmwarePackage;
import com.smartfridge.entity.OtaJobStatus;
import com.smartfridge.entity.OtaUpdateJob;
import com.smartfridge.exception.CommandDispatchException;
import com.smartfridge.exception.ResourceNotFoundException;
import com.smartfridge.mqtt.CommandExecutionStrategy;
import com.smartfridge.mqtt.Topics;
import com.smartfridge.repository.DeviceRepository;
import com.smartfridge.repository.OtaUpdateJobRepository;
import com.smartfridge.util.HardwareRevision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** OTA manager: compatibility gating, dual-bank update commands, job lifecycle, timeouts. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtaService {

    private static final int OTA_QOS = 1;

    private final FirmwareService firmwareService;
    private final DeviceService deviceService;
    private final DeviceRepository deviceRepository;
    private final OtaUpdateJobRepository jobRepository;
    private final CommandExecutionStrategy executionStrategy;
    private final FirmwareSigner signer;
    private final ObjectMapper objectMapper;
    private final MaintenanceProperties maintenanceProperties;
    private final Clock clock;

    // ------------------------------------------------------------ deployment

    @Transactional
    public OtaDeployResponse deploy(UUID firmwareId, OtaDeployRequest request) {
        FirmwarePackage firmware = firmwareService.getEntity(firmwareId);
        List<OtaDeployItem> results = new ArrayList<>();
        for (UUID deviceId : new LinkedHashSet<>(request.deviceIds())) {
            results.add(deployToDevice(firmware, deviceId));
        }
        int accepted = (int) results.stream().filter(OtaDeployItem::accepted).count();
        return new OtaDeployResponse(firmware.getId(), firmware.getVersion(), accepted,
                results.size() - accepted, results);
    }

    private OtaDeployItem deployToDevice(FirmwarePackage firmware, UUID deviceId) {
        Optional<Device> found = deviceRepository.findById(deviceId);
        if (found.isEmpty()) {
            return reject(deviceId, "Device not found");
        }
        Device device = found.get();

        if (device.getStatus() == DeviceStatus.UPDATING
                || jobRepository.existsByDeviceIdAndStatusIn(deviceId, FirmwareService.IN_FLIGHT)) {
            return reject(deviceId, "An update is already in progress for this device");
        }
        if (device.getStatus() == DeviceStatus.OFFLINE) {
            return reject(deviceId, "Device is offline");
        }
        if (firmware.getVersion().equals(device.getFirmwareVersion())) {
            return reject(deviceId, "Device already runs firmware " + firmware.getVersion());
        }
        String minRev = firmware.getMinHardwareRev();
        if (minRev != null && !minRev.isBlank() && HardwareRevision.compare(device.getHardwareRev(), minRev) < 0) {
            return reject(deviceId, "Hardware revision " + device.getHardwareRev()
                    + " is below the required minimum " + minRev);
        }

        OtaUpdateJob job = jobRepository.save(OtaUpdateJob.builder()
                .deviceId(deviceId)
                .firmwareId(firmware.getId())
                .targetVersion(firmware.getVersion())
                .status(OtaJobStatus.PENDING)
                .message("Queued")
                .build());

        try {
            executionStrategy.deliver(Topics.otaCommand(deviceId), buildCommand(job, firmware), OTA_QOS);
        } catch (CommandDispatchException ex) {
            job.setStatus(OtaJobStatus.FAILED);
            job.setMessage(truncate("Dispatch failed: " + ex.getMessage()));
            jobRepository.save(job);
            return new OtaDeployItem(deviceId, false, job.getId(), job.getMessage());
        }

        job.setStatus(OtaJobStatus.SENT);
        job.setMessage("OTA command sent");
        jobRepository.save(job);
        deviceService.markUpdating(deviceId);
        log.info("OTA job {} sent: device {} -> firmware {}", job.getId(), deviceId, firmware.getVersion());
        return new OtaDeployItem(deviceId, true, job.getId(), null);
    }

    private String buildCommand(OtaUpdateJob job, FirmwarePackage firmware) {
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("jobId", job.getId().toString());
        command.put("command", "OTA_UPDATE");
        command.put("version", firmware.getVersion());
        command.put("downloadUrl", firmwareService.downloadUrl(firmware));
        command.put("sha256", firmware.getChecksumSha256());
        command.put("sizeBytes", firmware.getSizeBytes());
        command.put("signature", signer.sign(firmware.getVersion(), firmware.getChecksumSha256()));
        command.put("strategy", "DUAL_BANK");
        command.put("issuedAt", Instant.now(clock).toString());
        try {
            return objectMapper.writeValueAsString(command);
        } catch (JsonProcessingException ex) {
            throw new CommandDispatchException("Could not serialise OTA command", ex);
        }
    }

    private OtaDeployItem reject(UUID deviceId, String reason) {
        return new OtaDeployItem(deviceId, false, null, reason);
    }

    // ---------------------------------------------------------- job queries

    @Transactional(readOnly = true)
    public OtaJobResponse getJob(UUID jobId) {
        return jobRepository.findById(jobId)
                .map(OtaJobResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("OTA job " + jobId + " not found"));
    }

    @Transactional(readOnly = true)
    public PageResponse<OtaJobResponse> listJobs(UUID deviceId, OtaJobStatus status, Pageable pageable) {
        Page<OtaUpdateJob> page;
        if (deviceId != null && status != null) {
            page = jobRepository.findByDeviceIdAndStatus(deviceId, status, pageable);
        } else if (deviceId != null) {
            page = jobRepository.findByDeviceId(deviceId, pageable);
        } else if (status != null) {
            page = jobRepository.findByStatus(status, pageable);
        } else {
            page = jobRepository.findAll(pageable);
        }
        return PageResponse.of(page, OtaJobResponse::from);
    }

    // ------------------------------------------------------- device feedback

    @Transactional
    public void handleStatusReport(UUID deviceId, OtaStatusPayload payload) {
        Optional<OtaUpdateJob> found = payload.jobId() != null
                ? jobRepository.findById(payload.jobId()).filter(j -> j.getDeviceId().equals(deviceId))
                : jobRepository.findFirstByDeviceIdAndStatusInOrderByCreatedAtDesc(deviceId,
                FirmwareService.IN_FLIGHT);
        if (found.isEmpty()) {
            log.warn("OTA status from device {} does not match any job: {}", deviceId, payload);
            return;
        }
        OtaUpdateJob job = found.get();
        if (job.getStatus() == OtaJobStatus.COMPLETED || job.getStatus() == OtaJobStatus.FAILED) {
            log.debug("Ignoring OTA status for already finished job {}", job.getId());
            return;
        }

        String status = payload.status() == null ? "" : payload.status().trim().toUpperCase(Locale.ROOT);
        switch (status) {
            case "DOWNLOADING", "INSTALLING" -> {
                job.setStatus(OtaJobStatus.DOWNLOADING);
                job.setMessage(truncate(payload.message() == null ? status : payload.message()));
                jobRepository.save(job);
            }
            case "COMPLETED", "SUCCESS" -> {
                job.setStatus(OtaJobStatus.COMPLETED);
                job.setMessage("Update installed");
                jobRepository.save(job);
                String version = payload.version() != null && !payload.version().isBlank()
                        ? payload.version().trim() : job.getTargetVersion();
                deviceService.completeFirmwareUpdate(deviceId, version);
                log.info("OTA job {} completed: device {} now on {}", job.getId(), deviceId, version);
            }
            case "FAILED", "ROLLED_BACK" -> {
                job.setStatus(OtaJobStatus.FAILED);
                job.setMessage(truncate(payload.message() == null ? "Device reported " + status : payload.message()));
                jobRepository.save(job);
                deviceService.restoreStatusAfterUpdate(deviceId);
                log.warn("OTA job {} failed on device {}: {}", job.getId(), deviceId, job.getMessage());
            }
            default -> log.warn("Unknown OTA status '{}' from device {}", payload.status(), deviceId);
        }
    }

    // ---------------------------------------------------------- maintenance

    @Transactional
    public int expireStaleJobs() {
        Instant cutoff = Instant.now(clock).minus(Duration.ofMinutes(maintenanceProperties.getOtaJobTimeoutMinutes()));
        List<OtaUpdateJob> stale = jobRepository.findByStatusInAndUpdatedAtBefore(FirmwareService.IN_FLIGHT, cutoff);
        for (OtaUpdateJob job : stale) {
            job.setStatus(OtaJobStatus.FAILED);
            job.setMessage("Timed out waiting for the device");
            jobRepository.save(job);
            if (deviceRepository.existsById(job.getDeviceId())) {
                deviceService.restoreStatusAfterUpdate(job.getDeviceId());
            }
            log.warn("OTA job {} timed out", job.getId());
        }
        return stale.size();
    }

    private String truncate(String text) {
        return text.length() <= 500 ? text : text.substring(0, 500);
    }
}
