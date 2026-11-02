-- V46__create_dish_ingredient_table.sql
-- Dish-level ingredient knowledge (BR-DISH-002, BR-DISH-003).
--
-- `dish` previously had no relationship to ingredients: ingredients hung off
-- `recipe` only, so nothing could express "spaghetti normally contains noodles".
-- This table is the curated association used to suggest ingredients when a member
-- writes a recipe for a dish, and to warn when a required one is missing.
--
-- `is_required` is advisory only — no recipe is rejected for omitting it.
-- `removed_at` is a tombstone: an admin withdrawing an entry must not have it
-- silently reinstated by a later recipe approval, so the row is kept and the
-- auto-seed path skips it.

CREATE TABLE dish_ingredient (
    dish_id UUID NOT NULL REFERENCES dish(id) ON DELETE CASCADE,
    ingredient_id UUID NOT NULL REFERENCES ingredient(id) ON DELETE CASCADE,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    removed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (dish_id, ingredient_id)
);

CREATE INDEX idx_dish_ingredient_dish_id ON dish_ingredient(dish_id);
CREATE INDEX idx_dish_ingredient_ingredient_id ON dish_ingredient(ingredient_id);
