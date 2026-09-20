-- V8__create_menu_tables.sql
-- Menu and Menu_Detail tables

CREATE TABLE menu (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('drafted','scheduled','cancelled','completed')),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE menu_detail (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    menu_id UUID NOT NULL REFERENCES menu(id) ON DELETE CASCADE,
    dish_id UUID NOT NULL REFERENCES dish(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    meal_type VARCHAR(20) NOT NULL CHECK (meal_type IN ('BREAKFAST','LUNCH','DINNER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (menu_id, date, meal_type)
);

CREATE INDEX idx_menu_user_id ON menu(user_id);
CREATE INDEX idx_menu_start_date ON menu(start_date);
CREATE INDEX idx_menu_detail_menu_id ON menu_detail(menu_id);
CREATE INDEX idx_menu_detail_dish_id ON menu_detail(dish_id);
CREATE INDEX idx_menu_detail_date_meal ON menu_detail(date, meal_type);