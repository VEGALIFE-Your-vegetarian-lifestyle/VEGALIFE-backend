-- V52__create_saved_item.sql
-- Saved posts and saved recipes (one bookmark table, one nullable target per row).
-- BR-SAVED-001: a member may save a post or a recipe at most once; saving is idempotent.

CREATE TABLE saved_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    post_id UUID REFERENCES post(id) ON DELETE CASCADE,
    recipe_id UUID REFERENCES recipe(id) ON DELETE CASCADE,
    saved_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT saved_item_exactly_one_target CHECK (
        (post_id IS NOT NULL)::int + (recipe_id IS NOT NULL)::int = 1
    )
);

CREATE UNIQUE INDEX uq_saved_item_post
    ON saved_item(user_id, post_id) WHERE post_id IS NOT NULL;

CREATE UNIQUE INDEX uq_saved_item_recipe
    ON saved_item(user_id, recipe_id) WHERE recipe_id IS NOT NULL;

CREATE INDEX idx_saved_item_user_saved_at ON saved_item(user_id, saved_at DESC);
