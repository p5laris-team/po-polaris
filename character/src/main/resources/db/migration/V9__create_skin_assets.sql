CREATE TABLE skin_assets (
    id                BIGSERIAL PRIMARY KEY,
    item_id           BIGINT      NOT NULL,
    character_type_id BIGINT      NOT NULL REFERENCES character_types (id),
    asset_type        VARCHAR(50) NOT NULL,
    asset_url         TEXT        NOT NULL,

    CONSTRAINT uq_skin_assets_item_character_asset_type
        UNIQUE (item_id, character_type_id, asset_type)
);

CREATE INDEX idx_skin_assets_item_character
    ON skin_assets (item_id, character_type_id);
