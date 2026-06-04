CREATE UNIQUE INDEX IF NOT EXISTS uq_share_cards_user_image_url
    ON share_cards (user_id, image_url)
    WHERE image_url IS NOT NULL;
