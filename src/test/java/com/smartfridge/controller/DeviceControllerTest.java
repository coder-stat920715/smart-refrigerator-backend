package com.smartfridge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfridge.dto.DeviceCreateRequest;
import com.smartfridge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeviceControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String body(String mac) throws Exception {
        return objectMapper.writeValueAsString(
                new DeviceCreateRequest(mac, "Kitchen Fridge", "FX-200", "A2", "1.0.0"));
    }

    @Test
    void registerThenFetchDevice() throws Exception {
        String response = mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON).content(body("aa:bb:cc:dd:ee:ff")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.macAddress").value("AA:BB:CC:DD:EE:FF"))
                .andExpect(jsonPath("$.status").value("OFFLINE"))
                .andExpect(jsonPath("$.operationalState").value("NORMAL_COOLING"))
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(get("/api/v1/devices/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kitchen Fridge"));

        mockMvc.perform(get("/api/v1/devices/" + id + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.online").value(false))
                .andExpect(jsonPath("$.activeFaultCount").value(0));

        mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void duplicateMacIsRejectedWithConflict() throws Exception {
        mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON).content(body("AA:BB:CC:DD:EE:01")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON).content(body("aa:bb:cc:dd:ee:01")))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidMacFailsValidation() throws Exception {
        mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON).content(body("not-a-mac")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").exists());
    }

    @Test
    void unknownDeviceReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/devices/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesDevice() throws Exception {
        String response = mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON).content(body("AA:BB:CC:DD:EE:02")))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(delete("/api/v1/devices/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/devices/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void commandToOfflineDeviceIsRejected() throws Exception {
        String response = mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON).content(body("AA:BB:CC:DD:EE:03")))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(post("/api/v1/devices/" + id + "/commands")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"FORCE_DEFROST\"}"))
                .andExpect(status().isConflict());
    }
}
