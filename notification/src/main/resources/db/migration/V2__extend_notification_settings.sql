ALTER TABLE notification_settings
    ADD COLUMN mission_offer_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN character_state_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN daily_reminder_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN quiet_hours_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN quiet_hours_start TIME NOT NULL DEFAULT '22:00',
    ADD COLUMN quiet_hours_end TIME NOT NULL DEFAULT '08:00';

ALTER TABLE notification_settings
    ADD CONSTRAINT uk_notification_settings_user_id UNIQUE (user_id);
