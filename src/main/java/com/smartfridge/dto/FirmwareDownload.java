package com.smartfridge.dto;

import org.springframework.core.io.Resource;

public record FirmwareDownload(Resource resource, String fileName, long sizeBytes, String checksum) {
}
