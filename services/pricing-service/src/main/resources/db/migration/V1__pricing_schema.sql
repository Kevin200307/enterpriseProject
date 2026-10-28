CREATE TABLE zones (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    code VARCHAR(12) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_zones_name UNIQUE (name),
    CONSTRAINT uq_zones_code UNIQUE (code),
    CONSTRAINT ck_zones_name_nonblank CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_zones_code_upper CHECK (code = upper(code) AND code ~ '^[A-Z0-9_-]+$')
);

CREATE TABLE zone_cities (
    zone_id UUID NOT NULL REFERENCES zones(id) ON DELETE CASCADE,
    city_name VARCHAR(120) NOT NULL,
    normalized_city VARCHAR(120) NOT NULL,
    CONSTRAINT pk_zone_cities PRIMARY KEY (zone_id, normalized_city),
    CONSTRAINT ck_zone_cities_normalized CHECK (normalized_city = lower(btrim(city_name))),
    CONSTRAINT ck_zone_cities_nonblank CHECK (length(normalized_city) > 0)
);
CREATE INDEX ix_zone_cities_normalized ON zone_cities(normalized_city);

CREATE TABLE pricing_rules (
    id UUID PRIMARY KEY,
    zone_from_id UUID NOT NULL REFERENCES zones(id) ON DELETE RESTRICT,
    zone_to_id UUID NOT NULL REFERENCES zones(id) ON DELETE RESTRICT,
    size_band VARCHAR(16) NOT NULL,
    base_fee NUMERIC(10,2) NOT NULL,
    per_kg NUMERIC(10,2) NOT NULL,
    min_fee NUMERIC(10,2) NOT NULL,
    max_weight_kg NUMERIC(10,3) NOT NULL,
    included_weight_kg NUMERIC(10,3) NOT NULL DEFAULT 1.00,
    express_surcharge_percent NUMERIC(5,2) NOT NULL DEFAULT 15.00,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_pricing_rules_size CHECK (size_band IN ('SMALL', 'MEDIUM', 'LARGE')),
    CONSTRAINT ck_pricing_rules_fees CHECK (base_fee >= 0 AND per_kg >= 0 AND min_fee >= 0),
    CONSTRAINT ck_pricing_rules_weights CHECK (max_weight_kg > 0 AND included_weight_kg >= 0),
    CONSTRAINT ck_pricing_rules_express CHECK (express_surcharge_percent >= 0),
    CONSTRAINT ck_pricing_rules_effective CHECK (effective_to IS NULL OR effective_from < effective_to),
    CONSTRAINT uq_pricing_rule_start UNIQUE (zone_from_id, zone_to_id, size_band, effective_from)
);
CREATE INDEX ix_pricing_rules_lookup ON pricing_rules(zone_from_id, zone_to_id, size_band, active, effective_from, effective_to);

-- Active city assignments must be unique even though inactive zones may retain the same city.
CREATE FUNCTION enforce_active_zone_city_unique() RETURNS trigger AS $$
DECLARE assigned_zone_active BOOLEAN;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext(NEW.normalized_city));
    SELECT active INTO assigned_zone_active FROM zones WHERE id = NEW.zone_id;
    IF assigned_zone_active AND EXISTS (
        SELECT 1 FROM zone_cities c JOIN zones z ON z.id = c.zone_id
        WHERE c.normalized_city = NEW.normalized_city AND z.active
          AND c.zone_id <> NEW.zone_id
    ) THEN
        RAISE EXCEPTION 'City already belongs to an active zone' USING ERRCODE = '23505';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_active_zone_city_unique
    BEFORE INSERT OR UPDATE OF zone_id, normalized_city ON zone_cities
    FOR EACH ROW EXECUTE FUNCTION enforce_active_zone_city_unique();

CREATE FUNCTION enforce_zone_activation_city_unique() RETURNS trigger AS $$
BEGIN
    IF NEW.active AND NOT OLD.active AND EXISTS (
        SELECT 1 FROM zone_cities own
        JOIN zone_cities other ON other.normalized_city = own.normalized_city AND other.zone_id <> own.zone_id
        JOIN zones z ON z.id = other.zone_id AND z.active
        WHERE own.zone_id = NEW.id
    ) THEN
        RAISE EXCEPTION 'An active zone already owns one of these cities' USING ERRCODE = '23505';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_zone_activation_city_unique
    BEFORE UPDATE OF active ON zones
    FOR EACH ROW EXECUTE FUNCTION enforce_zone_activation_city_unique();

-- Reject overlapping active effective periods for the same route and size.
CREATE FUNCTION enforce_pricing_rule_period_unique() RETURNS trigger AS $$
BEGIN
    IF NEW.active AND EXISTS (
        SELECT 1 FROM pricing_rules r
        WHERE r.active AND r.zone_from_id = NEW.zone_from_id AND r.zone_to_id = NEW.zone_to_id
          AND r.size_band = NEW.size_band AND r.id <> NEW.id
          AND tstzrange(r.effective_from, r.effective_to, '[)')
              && tstzrange(NEW.effective_from, NEW.effective_to, '[)')
    ) THEN
        RAISE EXCEPTION 'Active pricing rule periods may not overlap' USING ERRCODE = '23P01';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_pricing_rule_period_unique
    BEFORE INSERT OR UPDATE ON pricing_rules
    FOR EACH ROW EXECUTE FUNCTION enforce_pricing_rule_period_unique();

CREATE FUNCTION touch_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_zones_updated_at BEFORE UPDATE ON zones FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER trg_pricing_rules_updated_at BEFORE UPDATE ON pricing_rules FOR EACH ROW EXECUTE FUNCTION touch_updated_at();