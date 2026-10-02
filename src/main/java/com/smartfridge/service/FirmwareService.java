package com.smartfridge.service;

import com.smartfridge.config.FirmwareProperties;
import com.smartfridge.dto.FirmwareDownload;
import com.smartfridge.dto.FirmwareResponse;
import com.smartfridge.dto.PageResponse;
import com.smartfridge.entity.FirmwarePackage;
import com.smartfridge.entity.OtaJobStatus;
import com.smartfridge.exception.BadRequestException;
import com.smartfridge.exception.ConflictException;
import com.smartfridge.exception.DuplicateResourceException;
import com.smartfridge.exception.FirmwareStorageException;
import com.smartfridge.exception.ResourceNotFoundException;
import com.smartfridge.repository.FirmwarePackageRepository;
import com.smartfridge.repository.OtaUpdateJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirmwareService {

    static final List<OtaJobStatus> IN_FLIGHT = List.of(OtaJobStatus.PENDING, OtaJobStatus.SENT,
            OtaJobStatus.DOWNLOADING);

    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^\\d+\\.\\d+\\.\\d+([-+][A-Za-z0-9.]+)?$");

    private final FirmwarePackageRepository firmwareRepository;
    private final OtaUpdateJobRepository jobRepository;
    private final FirmwareProperties properties;

    @Transactional
    public FirmwareResponse upload(MultipartFile file, String version, String minHardwareRev, String releaseNotes) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Firmware file is required and must not be empty");
        }
        if (version == null || !VERSION_PATTERN.matcher(version.trim()).matches()) {
            throw new BadRequestException("version must look like 1.2.3 or 1.2.3-rc1");
        }
        if (minHardwareRev != null && minHardwareRev.trim().length() > 16) {
            throw new BadRequestException("minHardwareRev must be at most 16 characters");
        }
        if (releaseNotes != null && releaseNotes.length() > 2000) {
            throw new BadRequestException("releaseNotes must be at most 2000 characters");
        }
        String cleanVersion = version.trim();
        if (firmwareRepository.existsByVersion(cleanVersion)) {
            throw new DuplicateResourceException("Firmware version " + cleanVersion + " already exists");
        }

        Path root = root();
        Path dir = root.resolve(cleanVersion).normalize();
        if (!dir.startsWith(root)) {
            throw new BadRequestException("Invalid version");
        }
        String fileName = sanitizeFileName(file.getOriginalFilename());
        Path target = dir.resolve(fileName);

        long size;
        String checksum;
        try {
            Files.createDirectories(dir);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(file.getInputStream(), digest)) {
                size = Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            checksum = HexFormat.of().formatHex(digest.digest());
        } catch (IOException ex) {
            deleteQuietly(target);
            throw new FirmwareStorageException("Failed to store firmware binary", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }

        FirmwarePackage saved;
        try {
            saved = firmwareRepository.saveAndFlush(FirmwarePackage.builder()
                    .version(cleanVersion)
                    .fileName(fileName)
                    .storagePath(cleanVersion + "/" + fileName)
                    .checksumSha256(checksum)
                    .sizeBytes(size)
                    .minHardwareRev(minHardwareRev == null || minHardwareRev.isBlank() ? null : minHardwareRev.trim())
                    .releaseNotes(releaseNotes)
                    .build());
        } catch (RuntimeException ex) {
            deleteQuietly(target);
            throw ex;
        }
        log.info("Stored firmware {} ({} bytes, sha256={})", cleanVersion, size, checksum);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public FirmwarePackage getEntity(UUID id) {
        return firmwareRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Firmware " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public FirmwareResponse get(UUID id) {
        return toResponse(getEntity(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<FirmwareResponse> list(Pageable pageable) {
        return PageResponse.of(firmwareRepository.findAll(pageable), this::toResponse);
    }

    @Transactional
    public void delete(UUID id) {
        FirmwarePackage pkg = getEntity(id);
        if (jobRepository.existsByFirmwareIdAndStatusIn(id, IN_FLIGHT)) {
            throw new ConflictException("Firmware " + pkg.getVersion() + " has OTA jobs in progress");
        }
        firmwareRepository.delete(pkg);
        Path file = resolveStoragePath(pkg);
        deleteQuietly(file);
        try {
            Files.deleteIfExists(file.getParent());
        } catch (IOException ignored) {
            // directory not empty or already removed: nothing to do
        }
        log.info("Deleted firmware {}", pkg.getVersion());
    }

    @Transactional(readOnly = true)
    public FirmwareDownload prepareDownload(UUID id) {
        FirmwarePackage pkg = getEntity(id);
        Path file = resolveStoragePath(pkg);
        if (!Files.isReadable(file)) {
            throw new ResourceNotFoundException("Firmware binary for version " + pkg.getVersion() + " is missing");
        }
        return new FirmwareDownload(new FileSystemResource(file), pkg.getFileName(), pkg.getSizeBytes(),
                pkg.getChecksumSha256());
    }

    public String downloadUrl(FirmwarePackage pkg) {
        String base = properties.getPublicBaseUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/api/v1/firmware/" + pkg.getId() + "/download";
    }

    public FirmwareResponse toResponse(FirmwarePackage pkg) {
        return FirmwareResponse.from(pkg, downloadUrl(pkg));
    }

    // ---------------------------------------------------------------- helpers

    private Path root() {
        return Paths.get(properties.getStorageDir()).toAbsolutePath().normalize();
    }

    private Path resolveStoragePath(FirmwarePackage pkg) {
        Path root = root();
        Path resolved = root.resolve(pkg.getStoragePath()).normalize();
        if (!resolved.startsWith(root)) {
            throw new FirmwareStorageException("Invalid firmware storage path");
        }
        return resolved;
    }

    private String sanitizeFileName(String original) {
        String name = original == null ? "" : original;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.isBlank() || name.chars().allMatch(c -> c == '.')) {
            return "firmware.bin";
        }
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Could not delete {}: {}", path, ex.getMessage());
        }
    }
}
