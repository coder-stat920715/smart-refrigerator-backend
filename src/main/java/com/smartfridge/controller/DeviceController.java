package com.smartfridge.controller;

import com.smartfridge.dto.CommandRequest;
import com.smartfridge.dto.CommandResponse;
import com.smartfridge.dto.DeviceCreateRequest;
import com.smartfridge.dto.DeviceResponse;
import com.smartfridge.dto.DeviceStatusResponse;
import com.smartfridge.dto.DeviceUpdateRequest;
import com.smartfridge.dto.ErrorResponse;
import com.smartfridge.dto.PageResponse;
import com.smartfridge.entity.DeviceStatus;
import com.smartfridge.service.DeviceCommandService;
import com.smartfridge.service.DeviceService;
import com.smartfridge.util.PageRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Tag(name = "Devices", description = "Smart refrigerator MCU registry, status and remote commands")
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceCommandService commandService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new device")
    @ApiResponse(responseCode = "201", description = "Device registered")
    @ApiResponse(responseCode = "409", description = "MAC address already registered",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public DeviceResponse register(@Valid @RequestBody DeviceCreateRequest request) {
        return deviceService.register(request);
    }

    @GetMapping
    @Operation(summary = "List devices", description = "Paginated, optionally filtered by status")
    public PageResponse<DeviceResponse> list(@RequestParam(required = false) DeviceStatus status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return deviceService.list(status, PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a device by id")
    public DeviceResponse get(@PathVariable UUID id) {
        return deviceService.get(id);
    }

    @GetMapping("/by-mac/{mac}")
    @Operation(summary = "Get a device by MAC address")
    public DeviceResponse getByMac(@PathVariable String mac) {
        return deviceService.getByMac(mac);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update device metadata (name, model, hardware revision)")
    public DeviceResponse update(@PathVariable UUID id, @Valid @RequestBody DeviceUpdateRequest request) {
        return deviceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a device and all of its telemetry, faults and OTA history")
    public void delete(@PathVariable UUID id) {
        deviceService.delete(id);
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Online/offline status, operational state and active fault count")
    public DeviceStatusResponse status(@PathVariable UUID id) {
        return deviceService.getStatus(id);
    }

    @PostMapping("/{id}/commands")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Send a remote command (defrost, temperature targets, reboot, diagnostics)")
    @ApiResponse(responseCode = "202", description = "Command published to the device topic")
    @ApiResponse(responseCode = "409", description = "Device offline or updating")
    @ApiResponse(responseCode = "503", description = "MQTT broker unavailable")
    public CommandResponse command(@PathVariable UUID id, @Valid @RequestBody CommandRequest request) {
        return commandService.dispatch(id, request);
    }
}
