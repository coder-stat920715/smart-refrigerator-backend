package com.smartfridge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fault_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaultEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FaultType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FaultSeverity severity;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "trigger_value")
    private Double triggerValue;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
