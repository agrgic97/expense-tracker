CREATE TABLE category_rule (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    pattern     VARCHAR(100) NOT NULL,
    category_id BIGINT      NOT NULL REFERENCES category (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_category_rule_user_pattern ON category_rule (user_id, lower(pattern));
