package com.smartfridge.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HardwareRevisionTest {

    @Test
    void comparesNumericPartsNaturally() {
        assertThat(HardwareRevision.compare("A10", "A2")).isPositive();
        assertThat(HardwareRevision.compare("A2", "A10")).isNegative();
    }

    @Test
    void equalRevisionsCompareToZero() {
        assertThat(HardwareRevision.compare("A2", "a2")).isZero();
        assertThat(HardwareRevision.compare("2.1", "2.1")).isZero();
    }

    @Test
    void comparesDottedVersions() {
        assertThat(HardwareRevision.compare("2.10", "2.9")).isPositive();
        assertThat(HardwareRevision.compare("1.0", "1.0.1")).isNegative();
    }
}
