ALTER TABLE item_outbox_events ALTER COLUMN payload TYPE jsonb USING payload::jsonb;
