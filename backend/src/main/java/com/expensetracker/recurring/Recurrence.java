package com.expensetracker.recurring;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public enum Recurrence {
    WEEKLY(1, ChronoUnit.WEEKS),
    MONTHLY(1, ChronoUnit.MONTHS),
    QUARTERLY(3, ChronoUnit.MONTHS),
    YEARLY(1, ChronoUnit.YEARS);

    private final long step;
    private final ChronoUnit unit;

    Recurrence(long step, ChronoUnit unit) {
        this.step = step;
        this.unit = unit;
    }

    /**
     * The n-th date of a schedule, always counted from its start so short months do not shift later dates
     * (31 Jan → 28 Feb → 31 Mar).
     */
    public LocalDate occurrence(LocalDate start, long n) {
        return start.plus(n * step, unit);
    }
}
