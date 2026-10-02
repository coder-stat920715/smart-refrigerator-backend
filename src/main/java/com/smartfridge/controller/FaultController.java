package com.smartfridge.controller;

import com.smartfridge.dto.FaultEventResponse;
import com.smartfridge.dto.PageResponse;
import com.smartfridge.service.FaultService;
import com.smartfridge.util.PageRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/faults")
@RequiredArgsConstructor
@Tag(name = "Faults", description = "Detected anomalies and device-reported alerts")
public class FaultController {

    private final FaultService faultService;

    @GetMapping
    @Operation(summary = "List fault events", description = "Filter by device and/or active flag; newest first")
    public PageResponse<FaultEventResponse> list(@RequestParam(required = false) UUID deviceId,
                                                 @RequestParam(required = false) Boolean active,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return faultService.list(deviceId, active,
                PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "openedAt")));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a fault event")
    public FaultEventResponse get(@PathVariable UUID id) {
        return faultService.get(id);
    }

    @PostMapping("/{id}/resolve")
    @Operation(summary = "Manually resolve a fault")
    public FaultEventResponse resolve(@PathVariable UUID id) {
        return faultService.resolve(id);
    }
}
