package com.smartfridge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfridge.dto.CommandRequest;
import com.smartfridge.dto.CommandResponse;
import com.smartfridge.entity.CommandType;
import com.smartfridge.entity.Device;
import com.smartfridge.entity.DeviceStatus;
import com.smartfridge.exception.BadRequestException;
import com.smartfridge.exception.CommandDispatchException;
import com.smartfridge.exception.ConflictException;
import com.smartfridge.mqtt.CommandExecutionStrategy;
import com.smartfridge.mqtt.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Dispatches remote commands to devices using the configured delivery strategy. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCommandService {

    private static final int COMMAND_QOS = 1;

    private final DeviceService deviceService;
    private final CommandExecutionStrategy executionStrategy;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public CommandResponse dispatch(UUID deviceId, CommandRequest request) {
        Device device = deviceService.getEntity(deviceId);
        if (device.getStatus() == DeviceStatus.UPDATING) {
            throw new ConflictException("Device is installing a firmware update; commands are rejected until it completes");
        }
        if (device.getStatus() == DeviceStatus.OFFLINE) {
            throw new ConflictException("Device is offline; command not dispatched");
        }
        if (request.type() == CommandType.SET_TEMPERATURE_TARGET
                && request.fridgeTargetTemp() == null && request.freezerTargetTemp() == null) {
            throw new BadRequestException("SET_TEMPERATURE_TARGET requires fridgeTargetTemp and/or freezerTargetTemp");
        }

        UUID commandId = UUID.randomUUID();
        Instant now = Instant.now(clock);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("commandId", commandId.toString());
        body.put("command", request.type().name());
        body.put("issuedAt", now.toString());
        if (request.type() == CommandType.SET_TEMPERATURE_TARGET) {
            if (request.fridgeTargetTemp() != null) {
                body.put("fridgeTargetTemp", request.fridgeTargetTemp());
            }
            if (request.freezerTargetTemp() != null) {
                body.put("freezerTargetTemp", request.freezerTargetTemp());
            }
        }

        String topic = Topics.commands(deviceId);
        try {
            executionStrategy.deliver(topic, objectMapper.writeValueAsString(body), COMMAND_QOS);
        } catch (JsonProcessingException ex) {
            throw new CommandDispatchException("Could not serialise command", ex);
        }

        if (request.type() == CommandType.FORCE_DEFROST) {
            deviceService.markDefrostStarted(deviceId);
        }
        log.info("Dispatched {} to device {} via {}", request.type(), deviceId, executionStrategy.protocolName());
        return new CommandResponse(commandId, deviceId, request.type(), topic,
                executionStrategy.protocolName(), now);
    }
}
