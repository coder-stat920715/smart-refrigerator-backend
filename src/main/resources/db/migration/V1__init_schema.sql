-- Device registry ------------------------------------------------------------
CREATE TABLE devices (
    id                UUID         PRIMARY KEY,
    mac_address       VARCHAR(17)  NOT NULL UNIQUE,
    name              VARCHAR(100) NOT NULL,
    model             VARCHAR(64),
    hardware_rev      VARCHAR(16)  NOT NULL,
    firmware_version  VARCHAR(32)  NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    operational_state VARCHAR(24)  NOT NULL,
    last_seen_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_devices_status ON devices (status);
CREATE INDEX idx_devices_last_seen ON devices (last_seen_at);

-- Telemetry (time-series) ----------------------------------------------------
-- Primary key includes "time" so the table can be converted to a TimescaleDB hypertable.
CREATE TABLE telemetry_logs (
    id               BIGSERIAL,
    device_id        UUID             NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    "time"           TIMESTAMPTZ      NOT NULL,
    freezer_temp     DOUBLE PRECISION NOT NULL,
    fridge_temp      DOUBLE PRECISION NOT NULL,
    compressor_state BOOLEAN          NOT NULL,
    door_open        BOOLEAN          NOT NULL,
    PRIMARY KEY (id, "time")
);

CREATE INDEX idx_telemetry_device_time ON telemetry_logs (device_id, "time" DESC);
CREATE INDEX idx_telemetry_time ON telemetry_logs ("time");

-- Optional TimescaleDB acceleration: inert on plain PostgreSQL.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'timescaledb') THEN
        CREATE EXTENSION IF NOT EXISTS timescaledb;
        PERFORM create_hypertable('telemetry_logs', 'time', if_not_exists => TRUE, migrate_data => TRUE);
    END IF;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'TimescaleDB hypertable not created: %', SQLERRM;
END $$;

-- Faults ---------------------------------------------------------------------
CREATE TABLE fault_events (
    id            UUID             PRIMARY KEY,
    device_id     UUID             NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    type          VARCHAR(40)      NOT NULL,
    severity      VARCHAR(16)      NOT NULL,
    message       VARCHAR(500)     NOT NULL,
    trigger_value DOUBLE PRECISION,
    active        BOOLEAN          NOT NULL,
    opened_at     TIMESTAMPTZ      NOT NULL,
    resolved_at   TIMESTAMPTZ
);

CREATE INDEX idx_fault_events_device_active ON fault_events (device_id, active);
CREATE INDEX idx_fault_events_opened_at ON fault_events (opened_at DESC);

-- Firmware catalog -----------------------------------------------------------
CREATE TABLE firmware_packages (
    id               UUID          PRIMARY KEY,
    version          VARCHAR(32)   NOT NULL UNIQUE,
    file_name        VARCHAR(255)  NOT NULL,
    storage_path     VARCHAR(512)  NOT NULL,
    checksum_sha256  VARCHAR(64)   NOT NULL,
    size_bytes       BIGINT        NOT NULL,
    min_hardware_rev VARCHAR(16),
    release_notes    VARCHAR(2000),
    uploaded_at      TIMESTAMPTZ   NOT NULL
);

-- OTA jobs -------------------------------------------------------------------
CREATE TABLE ota_update_jobs (
    id             UUID         PRIMARY KEY,
    device_id      UUID         NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    firmware_id    UUID         REFERENCES firmware_packages (id) ON DELETE SET NULL,
    target_version VARCHAR(32)  NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    message        VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_ota_jobs_device ON ota_update_jobs (device_id, created_at DESC);
CREATE INDEX idx_ota_jobs_status ON ota_update_jobs (status, updated_at);
