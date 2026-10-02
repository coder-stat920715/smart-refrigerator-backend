package com.smartfridge.event;

import com.smartfridge.entity.TelemetryLog;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Observer pattern: decouples telemetry ingestion from alert rule processing. */
@Component
@RequiredArgsConstructor
public class TelemetryEventPublisher {

    private final ApplicationEventPublisher publisher;

    public void publish(TelemetryLog reading) {
        publisher.publishEvent(new TelemetryReceivedEvent(reading));
    }
}
