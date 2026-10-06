-- Keeps audit log for notifications

CREATE TABLE IF NOT EXISTS notification_logs (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    parcel_id UUID NOT NULL,
    tracking_number VARCHAR(64) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    template VARCHAR(64) NOT NULL,
    subject VARCHAR(255),
    content TEXT,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance & quick audit lookups
CREATE INDEX IF NOT EXISTS idx_notification_event_id ON notification_logs(event_id);
CREATE INDEX IF NOT EXISTS idx_notification_tracking_number ON notification_logs(tracking_number);
CREATE INDEX IF NOT EXISTS idx_notification_created_at ON notification_logs(created_at DESC);