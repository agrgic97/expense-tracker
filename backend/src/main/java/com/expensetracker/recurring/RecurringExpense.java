package com.expensetracker.recurring;

import com.expensetracker.category.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Template from which an entry is booked automatically on every date of its schedule. */
@Entity
@Table(name = "recurring_expense")
public class RecurringExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Recurrence recurrence;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_generated_date")
    private LocalDate lastGeneratedDate;

    @Column(name = "next_date")
    private LocalDate nextDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected RecurringExpense() {
    }

    public RecurringExpense(Long userId) {
        this.userId = userId;
    }

    /**
     * Applies new values and reschedules. Dates of the schedule after the last booked entry stay due, so a start
     * date in the past books the missed entries. When a paused template is resumed, only dates from
     * {@code today} on are booked.
     */
    public void update(BigDecimal amount, String description, Category category, Recurrence recurrence,
                       LocalDate startDate, LocalDate endDate, boolean active, LocalDate today) {
        boolean resumed = active && !this.active && id != null;
        this.amount = amount;
        this.description = description;
        this.category = category;
        this.recurrence = recurrence;
        this.startDate = startDate;
        this.endDate = endDate;
        this.active = active;
        this.nextDate = nextDateAfter(lastGeneratedDate, resumed ? today : null);
    }

    public void markGenerated(LocalDate date) {
        lastGeneratedDate = date;
        nextDate = nextDateAfter(date, null);
    }

    /** First date of the schedule after {@code after} and not before {@code notBefore}; null past the end date. */
    LocalDate nextDateAfter(LocalDate after, LocalDate notBefore) {
        LocalDate candidate = startDate;
        for (long n = 1; (after != null && !candidate.isAfter(after))
                || (notBefore != null && candidate.isBefore(notBefore)); n++) {
            candidate = recurrence.occurrence(startDate, n);
        }
        return endDate != null && candidate.isAfter(endDate) ? null : candidate;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public Recurrence getRecurrence() {
        return recurrence;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDate getLastGeneratedDate() {
        return lastGeneratedDate;
    }

    public LocalDate getNextDate() {
        return nextDate;
    }
}
