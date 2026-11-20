-- V55__create_restaurant_dish_table.sql
-- Issue #195: curated dishes attached to a restaurant, surfaced nested in the
-- restaurant detail/create/update responses. Plain M:N join table, no extra
-- attributes (no pricing/availability/ordering — out of scope).

CREATE TABLE restaurant_dish (
    restaurant_id UUID NOT NULL REFERENCES restaurant(id) ON DELETE CASCADE,
    dish_id UUID NOT NULL REFERENCES dish(id) ON DELETE CASCADE,
    PRIMARY KEY (restaurant_id, dish_id)
);

CREATE INDEX idx_restaurant_dish_dish_id ON restaurant_dish(dish_id);
