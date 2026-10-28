CREATE SEQUENCE parcel_tracking_seq START WITH 1 INCREMENT BY 1 MAXVALUE 2821109907455 NO CYCLE;
CREATE TABLE parcels (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    tracking_number VARCHAR(40) NOT NULL UNIQUE,
    customer_id UUID NOT NULL,
    assignee_id UUID,
    merchant_id UUID,
    pickup_contact_name VARCHAR(120) NOT NULL,
    pickup_phone VARCHAR(25) NOT NULL,
    pickup_line1 VARCHAR(255) NOT NULL,
    pickup_city VARCHAR(100) NOT NULL,
    pickup_postal_code VARCHAR(20) NOT NULL,
    pickup_zone VARCHAR(80) NOT NULL,
    dropoff_contact_name VARCHAR(120) NOT NULL,
    dropoff_phone VARCHAR(25) NOT NULL,
    dropoff_line1 VARCHAR(255) NOT NULL,
    dropoff_city VARCHAR(100) NOT NULL,
    dropoff_postal_code VARCHAR(20) NOT NULL,
    dropoff_zone VARCHAR(80) NOT NULL,
    size VARCHAR(20) NOT NULL CHECK (size IN ('SMALL','MEDIUM','LARGE')),
    weight_kg NUMERIC(10,3) NOT NULL CHECK (weight_kg > 0 AND weight_kg <= 1000),
    fee NUMERIC(12,2) NOT NULL CHECK (fee >= 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL CONSTRAINT parcels_status_check CHECK (status IN ('CREATED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED')),
    booking_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_parcel_customer_booking UNIQUE (customer_id, booking_key)
);
CREATE INDEX idx_parcel_merchant_created ON parcels(merchant_id, created_at DESC);
CREATE INDEX idx_parcel_customer_created ON parcels(customer_id, created_at DESC);
CREATE INDEX idx_parcel_assignee_created ON parcels(assignee_id, created_at DESC);
CREATE TABLE delivery_proofs (
    id UUID PRIMARY KEY,
    parcel_id UUID NOT NULL REFERENCES parcels(id),
    uploaded_by UUID NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    object_key VARCHAR(255) NOT NULL UNIQUE
);
CREATE INDEX idx_proof_parcel ON delivery_proofs(parcel_id);
CREATE TABLE parcel_status_history (
    id UUID PRIMARY KEY,
    parcel_id UUID NOT NULL REFERENCES parcels(id),
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    actor_id UUID NOT NULL,
    note VARCHAR(1000),
    proof_id UUID REFERENCES delivery_proofs(id),
    failed_reason VARCHAR(500),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_history_parcel_time ON parcel_status_history(parcel_id, occurred_at DESC);
CREATE TABLE parcel_outbox (
    id UUID PRIMARY KEY,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_outbox_pending ON parcel_outbox(published_at, created_at);
