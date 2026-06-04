DO $$
BEGIN
    IF to_regclass('public.mission_reward_outbox') IS NOT NULL THEN
        IF EXISTS (
            SELECT 1
            FROM mission_reward_outbox legacy
            WHERE NOT EXISTS (
                SELECT 1
                FROM mission_outbox_events current_events
                WHERE current_events.idempotency_key = legacy.idempotency_key
            )
        ) THEN
            RAISE EXCEPTION 'mission_reward_outbox has rows not migrated to mission_outbox_events';
        END IF;

        DROP TABLE mission_reward_outbox;
    END IF;
END $$;
