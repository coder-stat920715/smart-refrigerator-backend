package com.smartfridge.scheduler;

import com.smartfridge.config.MaintenanceProperties;
import com.smartfridge.config.TelemetryProperties;
import com.smartfridge.service.DeviceService;
import com.smartfridge.service.OtaService;
import com.smartfridge.service.TelemetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Periodic housekeeping: heartbeat supervision, OTA timeouts, telemetry retention. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaintenanceScheduler {

    private final DeviceService deviceService;
    private final OtaService otaService;
    private final TelemetryService telemetryService;
    private final MaintenanceProperties maintenanceProperties;
    private final TelemetryProperties telemetryProperties;

    @Scheduled(fixedDelayString = "${app.maintenance.check-interval-ms:30000}",
            initialDelayString = "${app.maintenance.initial-delay-ms:30000}")
    public void superviseHeartbeats() {
        try {
            deviceService.markStaleDevicesOffline(Duration.ofSeconds(maintenanceProperties.getHeartbeatTimeoutSeconds()));
        } catch (RuntimeException ex) {
            log.error("Heartbeat supervision failed", ex);
        }
    }

    @Scheduled(fixedDelayString = "${app.maintenance.check-interval-ms:30000}",
            initialDelayString = "${app.maintenance.initial-delay-ms:30000}")
    public void expireOtaJobs() {
        try {
            otaService.expireStaleJobs();
        } catch (RuntimeException ex) {
            log.error("OTA job expiry failed", ex);
        }
    }

    @Scheduled(cron = "${app.maintenance.retention-cron:0 30 3 * * *}")
    public void purgeOldTelemetry() {
        try {
            telemetryService.purgeOlderThanDays(telemetryProperties.getRetentionDays());
        } catch (RuntimeException ex) {
            log.error("Telemetry retention failed", ex);
        }
    }
}
