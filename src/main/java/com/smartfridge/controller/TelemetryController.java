package com.smartfridge.controller;

import com.smartfridge.dto.PageResponse;
import com.smartfridge.dto.TelemetryResponse;
import com.smartfridge.dto.TelemetryStatsResponse;
import com.smartfridge.service.TelemetryService;
import com.smartfridge.util.PageRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices/{deviceId}/telemetry")
@RequiredArgsConstructor
@Tag(name = "Telemetry", description = "Time-series queries over refrigerator sensor data")
public class TelemetryController {

    private final TelemetryService telemetryService;

    @GetMapping
    @Operation(summary = "Telemetry history (newest first)", description = "Defaults to the last 24 hours")
    public PageResponse<TelemetryResponse> history(
            @PathVariable UUID deviceId,
            @Parameter(description = "ISO-8601 instant, e.g. 2025-01-01T00:00:00Z")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "ISO-8601 instant")
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return telemetryService.history(deviceId, from, to,
                PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "time")));
    }

    @GetMapping("/latest")
    @Operation(summary = "Most recent accepted reading")
    public TelemetryResponse latest(@PathVariable UUID deviceId) {
        return telemetryService.latest(deviceId);
    }

    @GetMapping("/stats")
    @Operation(summary = "Aggregated statistics (avg/min/max temperatures, compressor duty cycle)")
    public TelemetryStatsResponse stats(@PathVariable UUID deviceId,
                                        @RequestParam(required = false) Instant from,
                                        @RequestParam(required = false) Instant to) {
        return telemetryService.stats(deviceId, from, to);
    }
}
