CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_app_user_username ON app_user (lower(username));

-- Rows created before authentication existed have no owner yet; the first registered user adopts them.
ALTER TABLE category ADD COLUMN user_id BIGINT REFERENCES app_user (id) ON DELETE CASCADE;
ALTER TABLE expense ADD COLUMN user_id BIGINT REFERENCES app_user (id) ON DELETE CASCADE;

ALTER TABLE category DROP CONSTRAINT category_name_key;
CREATE UNIQUE INDEX uq_category_user_name ON category (user_id, lower(name));
CREATE INDEX idx_category_user ON category (user_id);
CREATE INDEX idx_expense_user_date ON expense (user_id, expense_date);
