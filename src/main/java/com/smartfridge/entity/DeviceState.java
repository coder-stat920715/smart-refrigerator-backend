package com.smartfridge.entity;

/** Operational state of the appliance (State pattern). */
public enum DeviceState {
    NORMAL_COOLING,
    DEFROST_CYCLE,
    DOOR_ALARM,
    CRITICAL_FAULT
}
