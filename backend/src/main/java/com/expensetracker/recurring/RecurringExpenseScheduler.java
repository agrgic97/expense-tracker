package com.expensetracker.recurring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Books due recurring entries shortly after midnight and once at startup to catch up after downtime. */
@Component
public class RecurringExpenseScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurringExpenseScheduler.class);

    private final RecurringExpenseService service;

    public RecurringExpenseScheduler(RecurringExpenseService service) {
        this.service = service;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "${app.recurring.cron:0 5 0 * * *}")
    public void bookDue() {
        int booked = service.bookAllDue();
        if (booked > 0) {
            log.info("{} wiederkehrende Buchung(en) angelegt", booked);
        }
    }
}
