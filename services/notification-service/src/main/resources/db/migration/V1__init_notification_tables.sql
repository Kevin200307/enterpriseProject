-- SwiftDrop: Notification Service - Official Schema
-- V1__init_notification_tables.sql

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    parcel_id UUID NOT NULL,
    tracking_number VARCHAR(64) NOT NULL,
    channel VARCHAR(16) NOT NULL,            -- 'EMAIL', 'SMS'
    recipient_ref UUID NOT NULL,             -- User ID / Actor ID
    recipient VARCHAR(255) NOT NULL,         -- Masked email or phone
    status VARCHAR(32) NOT NULL,             -- 'PENDING', 'SENT', 'FAILED', 'SKIPPED'
    template VARCHAR(64) NOT NULL,           -- e.g. 'CREATED', 'DELIVERED'
    subject VARCHAR(255),                    -- Email subject line
    content TEXT,                            -- Message body
    attempts INT NOT NULL DEFAULT 0,         -- Retry attempt counter
    error_message TEXT,                      -- Failure reason if any
    sent_at TIMESTAMP WITH TIME ZONE,        -- Timestamp when successfully dispatched
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_event_channel_recipient UNIQUE (event_id, channel, recipient_ref)
);

-- Performance & Audit Indexes
CREATE INDEX IF NOT EXISTS idx_notifications_event_id ON notifications(event_id);
CREATE INDEX IF NOT EXISTS idx_notifications_tracking_number ON notifications(tracking_number);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_ref ON notifications(recipient_ref);
CREATE INDEX IF NOT EXISTS idx_notifications_status ON notifications(status);
CREATE INDEX IF NOT EXISTS idx_notifications_created_at ON notifications(created_at DESC);