package com.smartfridge.repository;

import com.smartfridge.entity.FirmwarePackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FirmwarePackageRepository extends JpaRepository<FirmwarePackage, UUID> {

    boolean existsByVersion(String version);

    Optional<FirmwarePackage> findByVersion(String version);
}
