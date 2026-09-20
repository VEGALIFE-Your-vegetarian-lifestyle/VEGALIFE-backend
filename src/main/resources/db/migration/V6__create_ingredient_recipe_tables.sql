-- V6__create_ingredient_recipe_tables.sql
-- Ingredient, Recipe, Recipe_Ingredient tables

CREATE TABLE ingredient (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    calories DECIMAL(10,2),
    protein_g DECIMAL(10,2),
    carbohydrate_g DECIMAL(10,2),
    fat_g DECIMAL(10,2),
    fiber_g DECIMAL(10,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE recipe (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dish_id UUID NOT NULL REFERENCES dish(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    instructions TEXT NOT NULL,
    prep_time_minutes INT,
    cook_time_minutes INT,
    servings INT NOT NULL,
    difficulty VARCHAR(20) CHECK (difficulty IN ('EASY','MEDIUM','HARD')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE recipe_ingredient (
    recipe_id UUID NOT NULL REFERENCES recipe(id) ON DELETE CASCADE,
    ingredient_id UUID NOT NULL REFERENCES ingredient(id) ON DELETE CASCADE,
    amount DECIMAL(10,3) NOT NULL,
    unit VARCHAR(30) NOT NULL,
    PRIMARY KEY (recipe_id, ingredient_id)
);

CREATE INDEX idx_recipe_user_id ON recipe(user_id);
CREATE INDEX idx_recipe_dish_id ON recipe(dish_id);
CREATE INDEX idx_recipe_active ON recipe(id) WHERE deleted_at IS NULL;