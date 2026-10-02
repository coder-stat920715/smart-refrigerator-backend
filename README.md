# Smart Refrigerator IoT & Telemetry Backend

Spring Boot 3.2 / Java 17 backend for managing a fleet of microcontroller-driven smart refrigerators:
telemetry ingestion over MQTT, fault detection, remote commands and OTA firmware updates (dual-bank).

## Quick start

```bash
# 1. Everything in Docker (PostgreSQL/TimescaleDB + EMQX + backend)
docker compose up --build

# 2. Or run only the infrastructure and the app locally
docker compose up -d postgres emqx
mvn spring-boot:run
```

* Swagger UI: http://localhost:8080/swagger-ui.html
* OpenAPI JSON: http://localhost:8080/v3/api-docs
* Health: http://localhost:8080/actuator/health
* EMQX dashboard: http://localhost:18083 (admin / public)

Run tests (Docker required, Testcontainers starts PostgreSQL): `mvn clean verify`

## MQTT topic contract

| Direction | Topic | Payload |
|---|---|---|
| device -> cloud | `appliance/{deviceId}/telemetry` | `{"freezerTemp":-18.5,"fridgeTemp":4.0,"compressorOn":true,"doorOpen":false}` |
| device -> cloud | `appliance/{deviceId}/alerts` | `{"code":"E21","message":"Evaporator fan stalled","severity":"CRITICAL"}` |
| device -> cloud | `appliance/{deviceId}/state` | `{"state":"DEFROST_CYCLE"}` (NORMAL_COOLING, DEFROST_CYCLE, DOOR_ALARM, CRITICAL_FAULT) |
| device -> cloud | `appliance/{deviceId}/ota/status` | `{"jobId":"<uuid>","version":"2.0.0","status":"DOWNLOADING\|COMPLETED\|FAILED","message":""}` |
| cloud -> device | `appliance/{deviceId}/commands` | `{"commandId":"..","command":"FORCE_DEFROST", ...}` |
| cloud -> device | `appliance/{deviceId}/ota/command` | `{"jobId","command":"OTA_UPDATE","version","downloadUrl","sha256","sizeBytes","signature","strategy":"DUAL_BANK"}` |

`deviceId` is the backend UUID returned by `POST /api/v1/devices`.
Telemetry is also the device heartbeat: a device with no message for `app.maintenance.heartbeat-timeout-seconds`
(default 90 s) is marked `OFFLINE`.

The OTA `signature` is `HMAC-SHA256(secret, "<version>:<sha256>")` (hex). The MCU verifies it, downloads the image
into the inactive flash bank, verifies SHA-256, swaps banks and reports `COMPLETED` (or rolls back and reports `FAILED`).

## Example flow

```bash
# register a device
curl -X POST localhost:8080/api/v1/devices -H 'Content-Type: application/json' \
  -d '{"macAddress":"AA:BB:CC:DD:EE:01","name":"Kitchen Fridge","model":"FX-200","hardwareRev":"A2","firmwareVersion":"1.0.0"}'

# simulate the device (pip install paho-mqtt)
python3 scripts/device_simulator.py --device-id <uuid-from-above> --host localhost

# query telemetry
curl "localhost:8080/api/v1/devices/<uuid>/telemetry/latest"
curl "localhost:8080/api/v1/devices/<uuid>/telemetry/stats"

# upload firmware and deploy OTA
curl -X POST localhost:8080/api/v1/firmware -F file=@fw.bin -F version=2.0.0 -F minHardwareRev=A1
curl -X POST localhost:8080/api/v1/firmware/<fwId>/deploy -H 'Content-Type: application/json' -d '{"deviceIds":["<uuid>"]}'

# remote command
curl -X POST localhost:8080/api/v1/devices/<uuid>/commands -H 'Content-Type: application/json' -d '{"type":"FORCE_DEFROST"}'
```

## Alert rules (configurable under `app.alerts`)

| Fault | Condition |
|---|---|
| `FRIDGE_OVER_TEMPERATURE` (CRITICAL) | fridge temperature above threshold (10 C) continuously for 15 minutes |
| `FREEZER_OVER_TEMPERATURE` (CRITICAL) | freezer temperature above -10 C continuously for 15 minutes |
| `DOOR_LEFT_OPEN` (WARNING) | door open continuously for 5 minutes |
| `COMPRESSOR_INEFFECTIVE` (CRITICAL) | compressor on for 6 hours with less than 1 C fridge temperature drop |
| `DEVICE_REPORTED` | any alert published by the MCU on the alerts topic |

Rules are evaluated from persisted telemetry, so they survive restarts and work across multiple backend instances.
Faults auto-resolve when the condition clears and can be resolved manually via `POST /api/v1/faults/{id}/resolve`.

## Production hardening notes

* Use `ssl://broker:8883` for `app.mqtt.broker-url`, enable mTLS and per-device ACLs on the broker, and set a real
  `app.firmware.signing-secret`.
* Put the firmware download endpoint behind a gateway or signed URLs, and add authentication (Spring Security / OAuth2) in front of the REST API.
* Several backend instances can share the load by setting `app.mqtt.shared-subscription-group` (MQTT 5 / EMQX shared subscriptions).
* Replace local firmware storage with S3 by changing only `FirmwareService`.
