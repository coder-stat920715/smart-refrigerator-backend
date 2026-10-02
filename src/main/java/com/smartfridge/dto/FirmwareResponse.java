package com.smartfridge.dto;

import com.smartfridge.entity.FirmwarePackage;

import java.time.Instant;
import java.util.UUID;

public record FirmwareResponse(
        UUID id,
        String version,
        String fileName,
        String checksumSha256,
        long sizeBytes,
        String downloadUrl,
        String minHardwareRev,
        String releaseNotes,
        Instant uploadedAt) {

    public static FirmwareResponse from(FirmwarePackage p, String downloadUrl) {
        return new FirmwareResponse(p.getId(), p.getVersion(), p.getFileName(), p.getChecksumSha256(),
                p.getSizeBytes(), downloadUrl, p.getMinHardwareRev(), p.getReleaseNotes(), p.getUploadedAt());
    }
}
