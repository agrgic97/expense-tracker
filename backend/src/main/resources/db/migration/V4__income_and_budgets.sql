-- A category is either for expenses or for income; an entry's kind follows its category.
ALTER TABLE category ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'EXPENSE' CHECK (kind IN ('EXPENSE', 'INCOME'));
-- Optional monthly spending limit, only used for expense categories
ALTER TABLE category ADD COLUMN monthly_budget NUMERIC(12, 2) CHECK (monthly_budget > 0);

INSERT INTO category (user_id, name, color, kind)
SELECT u.id, d.name, d.color, 'INCOME'
FROM app_user u
CROSS JOIN (VALUES ('Gehalt', '#059669'), ('Sonstige Einnahmen', '#0d9488')) AS d (name, color)
WHERE NOT EXISTS (SELECT 1 FROM category c WHERE c.user_id = u.id AND lower(c.name) = lower(d.name));
