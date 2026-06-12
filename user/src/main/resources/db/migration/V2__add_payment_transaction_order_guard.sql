CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_transactions_order
    ON payment_transactions(payment_order_id);
