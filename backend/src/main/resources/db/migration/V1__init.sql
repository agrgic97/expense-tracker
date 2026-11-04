CREATE TABLE category (
    id    BIGSERIAL PRIMARY KEY,
    name  VARCHAR(50) NOT NULL UNIQUE,
    color VARCHAR(7)  NOT NULL DEFAULT '#64748b'
);

CREATE TABLE expense (
    id           BIGSERIAL PRIMARY KEY,
    amount       NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    expense_date DATE           NOT NULL,
    description  VARCHAR(255)   NOT NULL,
    category_id  BIGINT         NOT NULL REFERENCES category (id),
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_expense_date ON expense (expense_date);
CREATE INDEX idx_expense_category ON expense (category_id);

INSERT INTO category (name, color) VALUES
    ('Lebensmittel', '#16a34a'),
    ('Wohnen',       '#2563eb'),
    ('Transport',    '#f59e0b'),
    ('Restaurant',   '#dc2626'),
    ('Freizeit',     '#9333ea'),
    ('Gesundheit',   '#0891b2'),
    ('Kleidung',     '#db2777'),
    ('Abonnements',  '#4f46e5'),
    ('Sonstiges',    '#64748b');
