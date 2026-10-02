package com.smartfridge.service;

import com.smartfridge.entity.DeviceState;
import org.springframework.stereotype.Component;

/**
 * State pattern for appliance operational states.
 * CRITICAL_FAULT dominates: only FAULTS_CLEARED (or a device-reported state) leaves it.
 */
@Component
public class DeviceStateMachine {

    public DeviceState transition(DeviceState current, StateEvent event) {
        return switch (event) {
            case DEFROST_STARTED -> current == DeviceState.CRITICAL_FAULT ? current : DeviceState.DEFROST_CYCLE;
            case DEFROST_COMPLETED -> current == DeviceState.DEFROST_CYCLE ? DeviceState.NORMAL_COOLING : current;
            case DOOR_ALARM_RAISED -> current == DeviceState.CRITICAL_FAULT ? current : DeviceState.DOOR_ALARM;
            case DOOR_ALARM_CLEARED -> current == DeviceState.DOOR_ALARM ? DeviceState.NORMAL_COOLING : current;
            case CRITICAL_FAULT_RAISED -> DeviceState.CRITICAL_FAULT;
            case FAULTS_CLEARED -> current == DeviceState.CRITICAL_FAULT ? DeviceState.NORMAL_COOLING : current;
        };
    }
}
