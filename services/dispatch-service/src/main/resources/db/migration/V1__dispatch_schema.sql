CREATE TABLE couriers (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    user_id UUID NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    vehicle_type VARCHAR(20) NOT NULL CHECK (vehicle_type IN ('BIKE','THREEWHEEL','VAN')),
    home_zone_code VARCHAR(80) NOT NULL CHECK (home_zone_code ~ '^[A-Z][A-Z0-9_-]{0,79}$'),
    phone VARCHAR(20) NOT NULL CHECK (phone ~ '^(\+94|0)[1-9][0-9]{8}$'),
    max_daily_load INTEGER NOT NULL CHECK (max_daily_load BETWEEN 1 AND 10000),
    open_load INTEGER NOT NULL DEFAULT 0 CHECK (open_load >= 0 AND open_load <= max_daily_load),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_courier_zone_active ON couriers(home_zone_code, active, vehicle_type);
CREATE TABLE dispatch_parcels (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    parcel_version BIGINT NOT NULL DEFAULT -1,
    tracking_number VARCHAR(40) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL CHECK (status IN ('CREATED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED')),
    assignee_user_id UUID,
    pickup_city VARCHAR(100) NOT NULL,
    dropoff_city VARCHAR(100) NOT NULL,
    zone_code VARCHAR(80) NOT NULL,
    size VARCHAR(20) NOT NULL CHECK (size IN ('SMALL','MEDIUM','LARGE')),
    weight_kg NUMERIC(10,3) NOT NULL CHECK (weight_kg > 0),
    snapshot TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    status_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_dispatch_queue ON dispatch_parcels(status, assignee_user_id, zone_code, created_at, id);
CREATE TABLE assignments (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    parcel_id UUID NOT NULL REFERENCES dispatch_parcels(id),
    active_parcel_id UUID UNIQUE REFERENCES dispatch_parcels(id),
    courier_id UUID NOT NULL REFERENCES couriers(id),
    assigned_by UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    close_reason VARCHAR(1000),
    status VARCHAR(30) NOT NULL,
    CONSTRAINT ck_active_assignment CHECK (
        (closed_at IS NULL AND active_parcel_id = parcel_id AND active_parcel_id IS NOT NULL)
        OR (closed_at IS NOT NULL AND active_parcel_id IS NULL))
);
CREATE INDEX idx_assignment_courier_time ON assignments(courier_id, assigned_at);
CREATE INDEX idx_assignment_parcel ON assignments(parcel_id, assigned_at);
CREATE INDEX idx_assignment_status_time ON assignments(status, completed_at, failed_at);
CREATE TABLE assignment_commands (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    parcel_id UUID NOT NULL REFERENCES dispatch_parcels(id),
    pending_parcel_id UUID UNIQUE REFERENCES dispatch_parcels(id),
    courier_id UUID REFERENCES couriers(id),
    expected_assignee UUID,
    base_parcel_version BIGINT NOT NULL,
    target_assignee UUID,
    requested_by UUID NOT NULL,
    request_id VARCHAR(100) NOT NULL,
    reason VARCHAR(1000),
    state VARCHAR(20) NOT NULL CHECK (state IN ('PENDING','CONFIRMED','REJECTED')),
    detail VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_pending_command CHECK (
        (state = 'PENDING' AND pending_parcel_id = parcel_id AND pending_parcel_id IS NOT NULL)
        OR (state <> 'PENDING' AND pending_parcel_id IS NULL))
);
CREATE INDEX idx_commands_pending ON assignment_commands(state, created_at);
CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);
