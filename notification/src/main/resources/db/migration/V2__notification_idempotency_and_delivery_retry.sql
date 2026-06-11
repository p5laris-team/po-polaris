ALTER TABLE notifications
    ADD COLUMN idempotency_key VARCHAR(255) NULL;

CREATE UNIQUE INDEX uk_notifications_idempotency_key
    ON notifications (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

ALTER TABLE notification_push_deliveries
    ADD COLUMN next_attempt_at TIMESTAMP NULL,
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;

DELETE FROM notification_push_deliveries
WHERE id IN (
    SELECT id
    FROM (
        SELECT
            id,
            ROW_NUMBER() OVER (
                PARTITION BY notification_id, fcm_device_token_id
                ORDER BY
                    CASE delivery_status
                        WHEN 'SENT' THEN 1
                        WHEN 'PENDING' THEN 2
                        WHEN 'FAILED' THEN 3
                        WHEN 'SKIPPED' THEN 4
                        ELSE 5
                    END,
                    updated_at DESC,
                    id DESC
            ) AS duplicate_order
        FROM notification_push_deliveries
        WHERE fcm_device_token_id IS NOT NULL
    ) ranked_deliveries
    WHERE duplicate_order > 1
);

DELETE FROM notification_push_deliveries
WHERE id IN (
    SELECT id
    FROM (
        SELECT
            id,
            ROW_NUMBER() OVER (
                PARTITION BY notification_id
                ORDER BY updated_at DESC, id DESC
            ) AS duplicate_order
        FROM notification_push_deliveries
        WHERE fcm_device_token_id IS NULL
    ) ranked_deliveries
    WHERE duplicate_order > 1
);

CREATE UNIQUE INDEX uk_notification_push_deliveries_notification_token
    ON notification_push_deliveries (notification_id, fcm_device_token_id)
    WHERE fcm_device_token_id IS NOT NULL;

CREATE UNIQUE INDEX uk_notification_push_deliveries_notification_skipped
    ON notification_push_deliveries (notification_id)
    WHERE fcm_device_token_id IS NULL;
