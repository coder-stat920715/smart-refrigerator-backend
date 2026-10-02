package com.smartfridge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "firmware_packages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FirmwarePackage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String version;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** Path relative to the configured firmware storage directory. */
    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "min_hardware_rev", length = 16)
    private String minHardwareRev;

    @Column(name = "release_notes", length = 2000)
    private String releaseNotes;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @PrePersist
    void onCreate() {
        if (this.uploadedAt == null) {
            this.uploadedAt = Instant.now();
        }
    }
}
