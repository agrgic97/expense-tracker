CREATE TABLE recurring_expense (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    amount              NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    description         VARCHAR(255)   NOT NULL,
    category_id         BIGINT         NOT NULL REFERENCES category (id),
    recurrence          VARCHAR(10)    NOT NULL CHECK (recurrence IN ('WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY')),
    start_date          DATE           NOT NULL,
    end_date            DATE,
    active              BOOLEAN        NOT NULL DEFAULT true,
    last_generated_date DATE,
    -- Next date an entry is due; null once the schedule has passed its end date
    next_date           DATE,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_recurring_expense_user ON recurring_expense (user_id);
CREATE INDEX idx_recurring_expense_due ON recurring_expense (next_date) WHERE active;

ALTER TABLE expense ADD COLUMN recurring_id BIGINT REFERENCES recurring_expense (id) ON DELETE SET NULL;
