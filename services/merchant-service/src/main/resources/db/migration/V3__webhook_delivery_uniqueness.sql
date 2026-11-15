CREATE UNIQUE INDEX uq_webhook_deliveries_event_merchant
    ON webhook_deliveries(event_id, merchant_id);
