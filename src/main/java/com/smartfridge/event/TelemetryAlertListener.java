package com.smartfridge.event;

import com.smartfridge.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryAlertListener {

    private final AlertService alertService;

    /** Runs after the ingestion transaction committed so the reading is visible to the rule queries. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTelemetry(TelemetryReceivedEvent event) {
        try {
            alertService.evaluate(event.reading());
        } catch (RuntimeException ex) {
            log.error("Alert evaluation failed for device {}", event.reading().getDeviceId(), ex);
        }
    }
}
