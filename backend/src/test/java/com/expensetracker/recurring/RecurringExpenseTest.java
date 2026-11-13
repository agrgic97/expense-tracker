package com.expensetracker.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RecurringExpenseTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);

    @Test
    void startsAtStartDate() {
        RecurringExpense r = template(Recurrence.MONTHLY, LocalDate.of(2026, 12, 1), null, true);

        assertThat(r.getNextDate()).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void monthlyScheduleKeepsDayOfMonthAfterShortMonths() {
        RecurringExpense r = template(Recurrence.MONTHLY, LocalDate.of(2026, 1, 31), null, true);

        r.markGenerated(LocalDate.of(2026, 1, 31));
        assertThat(r.getNextDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        r.markGenerated(LocalDate.of(2026, 2, 28));
        assertThat(r.getNextDate()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void quarterlyAndWeeklySteps() {
        RecurringExpense quarterly = template(Recurrence.QUARTERLY, LocalDate.of(2026, 1, 15), null, true);
        quarterly.markGenerated(LocalDate.of(2026, 1, 15));
        assertThat(quarterly.getNextDate()).isEqualTo(LocalDate.of(2026, 4, 15));

        RecurringExpense weekly = template(Recurrence.WEEKLY, LocalDate.of(2026, 11, 2), null, true);
        weekly.markGenerated(LocalDate.of(2026, 11, 2));
        assertThat(weekly.getNextDate()).isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    void endsAfterEndDate() {
        RecurringExpense r = template(Recurrence.MONTHLY, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 15), true);

        r.markGenerated(LocalDate.of(2026, 11, 1));

        assertThat(r.getNextDate()).isNull();
    }

    @Test
    void resumingSkipsDatesMissedWhilePaused() {
        RecurringExpense r = template(Recurrence.MONTHLY, LocalDate.of(2026, 1, 5), null, true);
        ReflectionTestUtils.setField(r, "id", 1L);
        r.markGenerated(LocalDate.of(2026, 6, 5));
        update(r, Recurrence.MONTHLY, LocalDate.of(2026, 1, 5), null, false);

        update(r, Recurrence.MONTHLY, LocalDate.of(2026, 1, 5), null, true);

        assertThat(r.getNextDate()).isEqualTo(LocalDate.of(2026, 12, 5));
    }

    @Test
    void changingTheScheduleContinuesAfterTheLastBookedDate() {
        RecurringExpense r = template(Recurrence.MONTHLY, LocalDate.of(2026, 1, 1), null, true);
        ReflectionTestUtils.setField(r, "id", 1L);
        r.markGenerated(LocalDate.of(2026, 11, 1));

        update(r, Recurrence.MONTHLY, LocalDate.of(2026, 1, 15), null, true);

        assertThat(r.getNextDate()).isEqualTo(LocalDate.of(2026, 11, 15));
    }

    private static RecurringExpense template(Recurrence recurrence, LocalDate start, LocalDate end, boolean active) {
        RecurringExpense r = new RecurringExpense(1L);
        update(r, recurrence, start, end, active);
        return r;
    }

    private static void update(RecurringExpense r, Recurrence recurrence, LocalDate start, LocalDate end, boolean active) {
        r.update(new BigDecimal("10.00"), "Test", null, recurrence, start, end, active, TODAY);
    }
}
