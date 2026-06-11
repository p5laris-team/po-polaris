ALTER TABLE notifications
    ADD COLUMN idempotency_key VARCHAR(255) NULL;

CREATE UNIQUE INDEX uk_notifications_idempotency_key
    ON notifications (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

ALTER TABLE notification_push_deliveries
    ADD COLUMN next_attempt_at TIMESTAMP NULL,
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uk_notification_push_deliveries_notification_token
    ON notification_push_deliveries (notification_id, fcm_device_token_id)
    WHERE fcm_device_token_id IS NOT NULL;

CREATE UNIQUE INDEX uk_notification_push_deliveries_notification_skipped
    ON notification_push_deliveries (notification_id)
    WHERE fcm_device_token_id IS NULL;
