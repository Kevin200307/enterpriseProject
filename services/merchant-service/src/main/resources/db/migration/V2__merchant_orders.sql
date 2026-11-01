CREATE TABLE merchant_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    merchant_ref VARCHAR(100) NOT NULL,
    parcel_id UUID NOT NULL,
    tracking_number VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    destination_city VARCHAR(100) NOT NULL,
    fee NUMERIC(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_merchant_order_ref UNIQUE (merchant_id, merchant_ref),
    CONSTRAINT uq_merchant_order_parcel UNIQUE (parcel_id)
);
CREATE INDEX idx_merchant_orders_merchant_created ON merchant_orders(merchant_id, created_at DESC);
