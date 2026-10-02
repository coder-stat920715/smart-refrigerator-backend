package com.smartfridge.controller;

import com.smartfridge.dto.FirmwareDownload;
import com.smartfridge.dto.FirmwareResponse;
import com.smartfridge.dto.OtaDeployRequest;
import com.smartfridge.dto.OtaDeployResponse;
import com.smartfridge.dto.PageResponse;
import com.smartfridge.service.FirmwareService;
import com.smartfridge.service.OtaService;
import com.smartfridge.util.PageRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/firmware")
@RequiredArgsConstructor
@Tag(name = "Firmware & OTA", description = "Firmware catalog, SHA-256 signing and OTA deployment")
public class FirmwareController {

    private final FirmwareService firmwareService;
    private final OtaService otaService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a firmware binary",
            description = "Streams the file to storage while computing its SHA-256 checksum")
    @ApiResponse(responseCode = "201", description = "Firmware stored")
    @ApiResponse(responseCode = "409", description = "Version already exists")
    public FirmwareResponse upload(
            @Parameter(description = "Firmware image (.bin)") @RequestPart("file") MultipartFile file,
            @Parameter(description = "Semantic version, e.g. 2.1.0") @RequestParam("version") String version,
            @Parameter(description = "Minimum compatible hardware revision, e.g. A2")
            @RequestParam(value = "minHardwareRev", required = false) String minHardwareRev,
            @RequestParam(value = "releaseNotes", required = false) String releaseNotes) {
        return firmwareService.upload(file, version, minHardwareRev, releaseNotes);
    }

    @GetMapping
    @Operation(summary = "List firmware packages")
    public PageResponse<FirmwareResponse> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return firmwareService.list(PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "uploadedAt")));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get firmware metadata including checksum")
    public FirmwareResponse get(@PathVariable UUID id) {
        return firmwareService.get(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a firmware package (rejected while OTA jobs are in progress)")
    public void delete(@PathVariable UUID id) {
        firmwareService.delete(id);
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download the firmware binary",
            description = "Used by devices during OTA. The SHA-256 is also returned in the X-Checksum-SHA256 header.")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        FirmwareDownload download = firmwareService.prepareDownload(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.fileName()).build().toString())
                .header("X-Checksum-SHA256", download.checksum())
                .body(download.resource());
    }

    @PostMapping("/{id}/deploy")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Trigger an OTA update on target devices over MQTT",
            description = "Per-device results explain why a device was skipped (offline, incompatible hardware, ...)")
    public OtaDeployResponse deploy(@PathVariable UUID id, @Valid @RequestBody OtaDeployRequest request) {
        return otaService.deploy(id, request);
    }
}
