package com.example.demo.event;

import com.example.demo.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class OvertimeSettledEventListener {

    private final SmsService smsService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOvertimeSettled(OvertimeSettledEvent event) {
        log.info("Received OvertimeSettledEvent after transaction commit. Dispatching SMS...");
        try {
            smsService.sendSms(event.getWorkerId(), event.getMonth(), event.getTotalAmount());
        } catch (Exception e) {
            log.error("Failed to send SMS to worker ID {} after successful settlement: {}", 
                    event.getWorkerId(), e.getMessage());
        }
    }
}
