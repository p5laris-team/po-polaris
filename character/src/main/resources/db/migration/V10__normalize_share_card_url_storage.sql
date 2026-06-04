-- Store only stable identifiers/keys in share_cards.
-- API responses compose full URLs from APP_PUBLIC_BASE_URL and S3_PUBLIC_DOMAIN.

UPDATE share_cards
SET image_url = regexp_replace(image_url, '^https://[^/]+/', '')
WHERE image_url LIKE 'https://%';

UPDATE share_cards
SET share_url = regexp_replace(share_url, '^.*/', '')
WHERE share_url LIKE 'http://%'
   OR share_url LIKE 'https://%'
   OR share_url LIKE '/share/%';
