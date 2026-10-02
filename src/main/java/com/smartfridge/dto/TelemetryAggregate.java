package com.smartfridge.dto;

/** Projection used by the aggregate JPQL query. */
public record TelemetryAggregate(
        Long samples,
        Double avgFridgeTemp,
        Double minFridgeTemp,
        Double maxFridgeTemp,
        Double avgFreezerTemp,
        Double minFreezerTemp,
        Double maxFreezerTemp,
        Long compressorOnSamples) {
}
