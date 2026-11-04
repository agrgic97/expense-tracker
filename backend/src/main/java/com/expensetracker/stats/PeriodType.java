package com.expensetracker.stats;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

public enum PeriodType {
    WEEK(ChronoUnit.WEEKS),
    MONTH(ChronoUnit.MONTHS),
    YEAR(ChronoUnit.YEARS);

    private final ChronoUnit unit;

    PeriodType(ChronoUnit unit) {
        this.unit = unit;
    }

    public ChronoUnit unit() {
        return unit;
    }

    /** First day of the period containing {@code date}. Weeks start on Monday (ISO-8601). */
    public LocalDate startOf(LocalDate date) {
        return switch (this) {
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
            case YEAR -> date.withDayOfYear(1);
        };
    }

    /** Unit of the chart buckets inside a period: days for week/month, months for year. */
    public ChronoUnit bucketUnit() {
        return this == YEAR ? ChronoUnit.MONTHS : ChronoUnit.DAYS;
    }
}
