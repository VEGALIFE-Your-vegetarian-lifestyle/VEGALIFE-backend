-- V35__post_embedding_pgvector.sql
-- Duplicate post detection storage (issue #156, ADR-015).
--
-- Holds the embedding the content filter already computes on every publish, so
-- one post can be compared against another. The topic filter itself is
-- untouched: it compares against centroids on the classpath, mean-centered, and
-- cannot be expressed as a pgvector query.

CREATE EXTENSION IF NOT EXISTS vector;

-- embedding is the RAW embed() output, not the mean-centered vector the topic
-- filter scores against. Post-to-post similarity must not carry the seed-corpus
-- mean.
--
-- model is the isolation mechanism: vectors from different embedding models
-- live in different vector spaces and are not comparable. Rows whose model
-- differs from the active configuration must be skipped, never compared, or a
-- provider change would silently produce meaningless similarities while looking
-- healthy.
--
-- VECTOR(768) is a hard coupling to app.filter.embedding.dimensions. Changing
-- the dimension requires a migration that rebuilds this column and every row.
CREATE TABLE post_embedding (
    post_id    UUID         PRIMARY KEY REFERENCES post(id),
    embedding  VECTOR(768)  NOT NULL,
    model      VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Supporting lookup for the model filter in the nearest-neighbour query.
CREATE INDEX idx_post_embedding_model ON post_embedding (model);

-- Cosine index. The search is ORDER BY embedding <=> $1 LIMIT 1; the LIMIT is
-- load-bearing rather than an optimisation, since without it pgvector does not
-- use this index.
CREATE INDEX idx_post_embedding_hnsw
    ON post_embedding USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
