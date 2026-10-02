package com.smartfridge.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Notification hook. Currently logs; replace or extend with e-mail, push, Slack or webhook delivery.
 */
@Slf4j
@Component
public class FaultNotificationListener {

    @EventListener
    public void onFaultOpened(FaultOpenedEvent event) {
        log.warn("FAULT NOTIFICATION severity={} type={} device={} fault={} message={}",
                event.severity(), event.type(), event.deviceId(), event.faultId(), event.message());
    }
}
