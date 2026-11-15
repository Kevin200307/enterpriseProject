ALTER TABLE parcel_outbox ADD COLUMN routing_key VARCHAR(100) NOT NULL DEFAULT 'parcel.status.changed.v1';
CREATE TABLE parcel_assignment_commands (
    id UUID PRIMARY KEY,
    parcel_id UUID NOT NULL REFERENCES parcels(id),
    expected_assignee UUID,
    assignee_user_id UUID,
    requested_by UUID NOT NULL,
    reason VARCHAR(1000),
    parcel_version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
