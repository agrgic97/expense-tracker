package com.expensetracker.stats;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Inclusive date range. */
public record DateRange(LocalDate start, LocalDate end) {

    public static DateRange of(PeriodType type, LocalDate date) {
        LocalDate start = type.startOf(date);
        return new DateRange(start, start.plus(1, type.unit()).minusDays(1));
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    public long days() {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    public List<DateRange> split(ChronoUnit unit) {
        List<DateRange> parts = new ArrayList<>();
        for (LocalDate cursor = start; !cursor.isAfter(end); cursor = cursor.plus(1, unit)) {
            LocalDate partEnd = cursor.plus(1, unit).minusDays(1);
            parts.add(new DateRange(cursor, partEnd.isAfter(end) ? end : partEnd));
        }
        return parts;
    }
}
