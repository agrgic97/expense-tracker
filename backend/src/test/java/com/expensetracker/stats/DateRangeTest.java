package com.expensetracker.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class DateRangeTest {

    @Test
    void weekStartsOnMondayAndEndsOnSunday() {
        DateRange week = DateRange.of(PeriodType.WEEK, LocalDate.of(2026, 11, 4));

        assertThat(week.start()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(week.end()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(week.days()).isEqualTo(7);
    }

    @Test
    void monthCoversWholeCalendarMonthIncludingLeapDay() {
        DateRange month = DateRange.of(PeriodType.MONTH, LocalDate.of(2028, 2, 15));

        assertThat(month.start()).isEqualTo(LocalDate.of(2028, 2, 1));
        assertThat(month.end()).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void yearSplitsIntoTwelveMonths() {
        DateRange year = DateRange.of(PeriodType.YEAR, LocalDate.of(2026, 6, 1));

        assertThat(year.split(ChronoUnit.MONTHS)).hasSize(12)
                .last().isEqualTo(new DateRange(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31)));
    }
}
