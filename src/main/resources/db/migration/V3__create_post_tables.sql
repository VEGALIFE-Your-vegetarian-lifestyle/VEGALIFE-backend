-- V3__create_post_tables.sql
-- Post, Post_Category, Post_Media, Post_Recipe tables

CREATE TABLE post (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    location_id UUID REFERENCES location(id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    featured_image_url TEXT,
    status VARCHAR(20) NOT NULL CHECK (status IN ('created','processed','published','unpublished','hidden')),
    view_count INT NOT NULL DEFAULT 0,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE post_category (
    post_id UUID NOT NULL REFERENCES post(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES category(id) ON DELETE CASCADE,
    PRIMARY KEY (post_id, category_id)
);

CREATE TABLE post_media (
    post_id UUID NOT NULL REFERENCES post(id) ON DELETE CASCADE,
    media_id UUID NOT NULL REFERENCES media(id) ON DELETE CASCADE,
    PRIMARY KEY (post_id, media_id)
);

CREATE TABLE post_recipe (
    post_id UUID NOT NULL REFERENCES post(id) ON DELETE CASCADE,
    recipe_id UUID NOT NULL REFERENCES recipe(id) ON DELETE CASCADE,
    PRIMARY KEY (post_id, recipe_id)
);

CREATE INDEX idx_post_user_id ON post(user_id);
CREATE INDEX idx_post_location_id ON post(location_id);
CREATE INDEX idx_post_status ON post(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_post_published_at ON post(published_at);