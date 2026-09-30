-- V22__add_ingredient_dish_name_unique.sql
-- Case-insensitive uniqueness for ingredient and active dish names (BR-RECP-002).

CREATE UNIQUE INDEX idx_ingredient_name_unique ON ingredient (lower(name));

CREATE UNIQUE INDEX idx_dish_active_name_unique ON dish (lower(name)) WHERE deleted_at IS NULL;
