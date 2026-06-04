-- Align character asset keys with the S3/CloudFront asset key convention.
--
-- Character core images:
--   assets/characters/{character}/core/character-{character}-{state}.png
--
-- Character status images:
--   assets/characters/{character}/status/character-{character}-{state}.png

UPDATE character_assets
SET asset_type = 'LONELY'
WHERE asset_type = 'SAD';

WITH desired_assets AS (
    SELECT
        ct.id AS character_type_id,
        asset.asset_type,
        'assets/characters/'
            || LOWER(ct.code)
            || '/'
            || asset.asset_group
            || '/character-'
            || LOWER(ct.code)
            || '-'
            || asset.file_state
            || '.png' AS asset_url
    FROM character_types ct
    CROSS JOIN (
        VALUES
            ('IDLE', 'core', 'idle'),
            ('HAPPY', 'core', 'happy'),
            ('SLEEPY', 'core', 'sleepy'),
            ('HUNGRY', 'status', 'hungry'),
            ('LOW_ENERGY', 'status', 'low-energy'),
            ('LONELY', 'status', 'lonely')
    ) AS asset(asset_type, asset_group, file_state)
)
UPDATE character_assets ca
SET asset_url = da.asset_url
FROM desired_assets da
WHERE ca.character_type_id = da.character_type_id
  AND ca.asset_type = da.asset_type;

WITH desired_assets AS (
    SELECT
        ct.id AS character_type_id,
        asset.asset_type,
        'assets/characters/'
            || LOWER(ct.code)
            || '/'
            || asset.asset_group
            || '/character-'
            || LOWER(ct.code)
            || '-'
            || asset.file_state
            || '.png' AS asset_url
    FROM character_types ct
    CROSS JOIN (
        VALUES
            ('IDLE', 'core', 'idle'),
            ('HAPPY', 'core', 'happy'),
            ('SLEEPY', 'core', 'sleepy'),
            ('HUNGRY', 'status', 'hungry'),
            ('LOW_ENERGY', 'status', 'low-energy'),
            ('LONELY', 'status', 'lonely')
    ) AS asset(asset_type, asset_group, file_state)
)
INSERT INTO character_assets (character_type_id, asset_type, asset_url)
SELECT da.character_type_id, da.asset_type, da.asset_url
FROM desired_assets da
WHERE NOT EXISTS (
    SELECT 1
    FROM character_assets ca
    WHERE ca.character_type_id = da.character_type_id
      AND ca.asset_type = da.asset_type
);
