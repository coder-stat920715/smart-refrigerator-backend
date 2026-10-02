package com.smartfridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfridge.dto.FirmwareResponse;
import com.smartfridge.dto.OtaDeployRequest;
import com.smartfridge.dto.OtaDeployResponse;
import com.smartfridge.dto.OtaStatusPayload;
import com.smartfridge.entity.Device;
import com.smartfridge.entity.DeviceState;
import com.smartfridge.entity.DeviceStatus;
import com.smartfridge.entity.OtaJobStatus;
import com.smartfridge.entity.OtaUpdateJob;
import com.smartfridge.mqtt.LoggingCommandExecutionStrategy;
import com.smartfridge.repository.DeviceRepository;
import com.smartfridge.repository.OtaUpdateJobRepository;
import com.smartfridge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FirmwareOtaFlowTest extends AbstractIntegrationTest {

    /** SHA-256 of the ASCII string "hello". */
    private static final String HELLO_SHA256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    @Autowired
    private FirmwareService firmwareService;
    @Autowired
    private OtaService otaService;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private OtaUpdateJobRepository jobRepository;
    @Autowired
    private LoggingCommandExecutionStrategy loggingStrategy;
    @Autowired
    private FirmwareSigner signer;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private MockMultipartFile binary() {
        return new MockMultipartFile("file", "fw.bin", "application/octet-stream",
                "hello".getBytes(StandardCharsets.UTF_8));
    }

    private Device device(String mac, String hardwareRev, DeviceStatus status) {
        return deviceRepository.save(Device.builder()
                .macAddress(mac)
                .name("OTA test " + mac)
                .hardwareRev(hardwareRev)
                .firmwareVersion("1.0.0")
                .status(status)
                .operationalState(DeviceState.NORMAL_COOLING)
                .build());
    }

    @Test
    void uploadComputesSha256Checksum() {
        FirmwareResponse response = firmwareService.upload(binary(), "2.0.0", "A1", "Initial release");

        assertThat(response.checksumSha256()).isEqualTo(HELLO_SHA256);
        assertThat(response.sizeBytes()).isEqualTo(5);
        assertThat(response.downloadUrl()).endsWith("/api/v1/firmware/" + response.id() + "/download");
    }

    @Test
    void fullOtaLifecycleUpdatesDeviceFirmware() {
        FirmwareResponse firmware = firmwareService.upload(binary(), "2.0.0", "A1", null);
        Device eligible = device("AA:BB:CC:DD:EE:30", "A2", DeviceStatus.ONLINE);
        Device tooOld = device("AA:BB:CC:DD:EE:31", "A0", DeviceStatus.ONLINE);
        Device offline = device("AA:BB:CC:DD:EE:32", "A2", DeviceStatus.OFFLINE);

        OtaDeployResponse result = otaService.deploy(firmware.id(),
                new OtaDeployRequest(List.of(eligible.getId(), tooOld.getId(), offline.getId(), UUID.randomUUID())));

        assertThat(result.accepted()).isEqualTo(1);
        assertThat(result.rejected()).isEqualTo(3);

        UUID jobId = result.results().get(0).jobId();
        assertThat(jobId).isNotNull();
        OtaUpdateJob job = jobRepository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(OtaJobStatus.SENT);
        assertThat(deviceRepository.findById(eligible.getId()).orElseThrow().getStatus())
                .isEqualTo(DeviceStatus.UPDATING);

        String expectedSignature = signer.sign("2.0.0", HELLO_SHA256);
        assertThat(loggingStrategy.getSentMessages()).anySatisfy(message -> {
            assertThat(message).contains("appliance/" + eligible.getId() + "/ota/command");
            assertThat(message).contains(HELLO_SHA256).contains(expectedSignature).contains("DUAL_BANK");
        });

        otaService.handleStatusReport(eligible.getId(), new OtaStatusPayload(jobId, null, "DOWNLOADING", null));
        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(OtaJobStatus.DOWNLOADING);

        otaService.handleStatusReport(eligible.getId(), new OtaStatusPayload(jobId, "2.0.0", "COMPLETED", null));
        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(OtaJobStatus.COMPLETED);
        Device updated = deviceRepository.findById(eligible.getId()).orElseThrow();
        assertThat(updated.getFirmwareVersion()).isEqualTo("2.0.0");
        assertThat(updated.getStatus()).isEqualTo(DeviceStatus.ONLINE);
    }

    @Test
    void failedUpdateKeepsOldFirmwareAndRestoresStatus() {
        FirmwareResponse firmware = firmwareService.upload(binary(), "2.1.0", null, null);
        Device target = device("AA:BB:CC:DD:EE:33", "A2", DeviceStatus.ONLINE);

        OtaDeployResponse result = otaService.deploy(firmware.id(), new OtaDeployRequest(List.of(target.getId())));
        UUID jobId = result.results().get(0).jobId();

        otaService.handleStatusReport(target.getId(),
                new OtaStatusPayload(jobId, "2.1.0", "FAILED", "Checksum mismatch, rolled back to bank A"));

        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(OtaJobStatus.FAILED);
        Device after = deviceRepository.findById(target.getId()).orElseThrow();
        assertThat(after.getFirmwareVersion()).isEqualTo("1.0.0");
        assertThat(after.getStatus()).isEqualTo(DeviceStatus.ONLINE);
    }

    @Test
    void multipartUploadAndDownloadEndpoints() throws Exception {
        String response = mockMvc.perform(multipart("/api/v1/firmware").file(binary())
                        .param("version", "3.0.0").param("minHardwareRev", "A1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checksumSha256").value(HELLO_SHA256))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(get("/api/v1/firmware/" + id + "/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Checksum-SHA256", HELLO_SHA256))
                .andExpect(content().bytes("hello".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(multipart("/api/v1/firmware").file(binary()).param("version", "3.0.0"))
                .andExpect(status().isConflict());
        mockMvc.perform(multipart("/api/v1/firmware").file(binary()).param("version", "bad-version"))
                .andExpect(status().isBadRequest());
    }
}
