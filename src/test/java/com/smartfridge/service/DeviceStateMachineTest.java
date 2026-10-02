package com.smartfridge.service;

import com.smartfridge.entity.DeviceState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceStateMachineTest {

    private final DeviceStateMachine machine = new DeviceStateMachine();

    @Test
    void defrostCycleStartsAndFinishes() {
        DeviceState s = machine.transition(DeviceState.NORMAL_COOLING, StateEvent.DEFROST_STARTED);
        assertThat(s).isEqualTo(DeviceState.DEFROST_CYCLE);
        assertThat(machine.transition(s, StateEvent.DEFROST_COMPLETED)).isEqualTo(DeviceState.NORMAL_COOLING);
    }

    @Test
    void criticalFaultDominatesDoorAlarmAndDefrost() {
        assertThat(machine.transition(DeviceState.CRITICAL_FAULT, StateEvent.DOOR_ALARM_RAISED))
                .isEqualTo(DeviceState.CRITICAL_FAULT);
        assertThat(machine.transition(DeviceState.CRITICAL_FAULT, StateEvent.DEFROST_STARTED))
                .isEqualTo(DeviceState.CRITICAL_FAULT);
    }

    @Test
    void faultsClearedLeavesCriticalFaultOnly() {
        assertThat(machine.transition(DeviceState.CRITICAL_FAULT, StateEvent.FAULTS_CLEARED))
                .isEqualTo(DeviceState.NORMAL_COOLING);
        assertThat(machine.transition(DeviceState.DEFROST_CYCLE, StateEvent.FAULTS_CLEARED))
                .isEqualTo(DeviceState.DEFROST_CYCLE);
    }

    @Test
    void doorAlarmClearsBackToNormal() {
        DeviceState s = machine.transition(DeviceState.NORMAL_COOLING, StateEvent.DOOR_ALARM_RAISED);
        assertThat(s).isEqualTo(DeviceState.DOOR_ALARM);
        assertThat(machine.transition(s, StateEvent.DOOR_ALARM_CLEARED)).isEqualTo(DeviceState.NORMAL_COOLING);
    }
}
