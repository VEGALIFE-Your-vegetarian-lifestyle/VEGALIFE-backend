-- V17__add_post_type_and_video_url.sql
-- BR-CONTENT-002: every post is declared as blog or video at creation

ALTER TABLE post
    ADD COLUMN type VARCHAR(10) NOT NULL DEFAULT 'blog' CHECK (type IN ('blog','video')),
    ADD COLUMN video_url TEXT;
