package com.smartfridge.controller;

import com.smartfridge.dto.OtaJobResponse;
import com.smartfridge.dto.PageResponse;
import com.smartfridge.entity.OtaJobStatus;
import com.smartfridge.service.OtaService;
import com.smartfridge.util.PageRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ota/jobs")
@RequiredArgsConstructor
@Tag(name = "Firmware & OTA", description = "Firmware catalog, SHA-256 signing and OTA deployment")
public class OtaJobController {

    private final OtaService otaService;

    @GetMapping
    @Operation(summary = "List OTA jobs", description = "Filter by device and/or status; newest first")
    public PageResponse<OtaJobResponse> list(@RequestParam(required = false) UUID deviceId,
                                             @RequestParam(required = false) OtaJobStatus status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return otaService.listJobs(deviceId, status,
                PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an OTA job")
    public OtaJobResponse get(@PathVariable UUID id) {
        return otaService.getJob(id);
    }
}
