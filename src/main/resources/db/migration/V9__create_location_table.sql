-- V9__create_location_table.sql
-- Location table

CREATE TABLE location (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    latitude DECIMAL(9,6) NOT NULL,
    longitude DECIMAL(9,6) NOT NULL,
    phone_number VARCHAR(20),
    place_type VARCHAR(20) NOT NULL CHECK (place_type IN ('restaurant','grocery','cafe','market')),
    opening_hours JSONB,
    website_url TEXT,
    rating DECIMAL(2,1),
    price_level INT CHECK (price_level BETWEEN 1 AND 4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_location_active ON location(id) WHERE deleted_at IS NULL;
CREATE INDEX idx_location_place_type ON location(place_type);
CREATE INDEX idx_location_coords ON location(latitude, longitude);
CREATE INDEX idx_location_opening_hours ON location USING GIN (opening_hours);