-- =============================================================================
-- V2__seed_roles.sql
-- SwiftDrop Identity Service: Seed standard system roles
-- Matches contracts.domain.Role enum values
-- =============================================================================

INSERT INTO roles (id, description) VALUES
    ('CUSTOMER', 'Standard consumer who can estimate pricing, book parcel deliveries, and track shipments'),
    ('MERCHANT', 'Commercial business partner with access to bulk bookings, merchant API keys, and webhooks'),
    ('COURIER', 'Field driver who accepts assignments, updates parcel delivery milestones, and uploads proof of delivery'),
    ('DISPATCHER', 'Logistics coordinator who manages parcel routing, driver manifests, and courier assignments'),
    ('ADMIN', 'System administrator with user governance, role assignment, and platform configuration access')
ON CONFLICT (id) DO UPDATE SET
    description = EXCLUDED.description;
