package com.smartfridge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "telemetry_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelemetryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "time", nullable = false)
    private Instant time;

    @Column(name = "freezer_temp", nullable = false)
    private Double freezerTemp;

    @Column(name = "fridge_temp", nullable = false)
    private Double fridgeTemp;

    @Column(name = "compressor_state", nullable = false)
    private Boolean compressorOn;

    @Column(name = "door_open", nullable = false)
    private Boolean doorOpen;
}
