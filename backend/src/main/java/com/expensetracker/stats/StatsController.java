package com.expensetracker.stats;

import com.expensetracker.stats.StatsDtos.Summary;
import com.expensetracker.stats.StatsDtos.Trend;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService service;
    private final Clock clock;

    public StatsController(StatsService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/summary")
    public Summary summary(
            @RequestParam(defaultValue = "MONTH") PeriodType period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.summary(period, date != null ? date : LocalDate.now(clock));
    }

    @GetMapping("/trend")
    public Trend trend(
            @RequestParam(defaultValue = "MONTH") PeriodType period,
            @RequestParam(defaultValue = "12") int count,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.trend(period, date != null ? date : LocalDate.now(clock), count);
    }
}
