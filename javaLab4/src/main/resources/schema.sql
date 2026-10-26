-- PostgreSQL: run once against your lab database using pgAdmin's Query Tool.
-- Coordinates are stored as latitude and longitude in decimal degrees.
-- Capacity and delivery quantity must use the same unit (for example, kg).
BEGIN;

CREATE TABLE depots (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (btrim(name) <> ''),
    latitude NUMERIC(9, 6) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude NUMERIC(9, 6) NOT NULL CHECK (longitude BETWEEN -180 AND 180)
);

CREATE TABLE drivers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (btrim(name) <> ''),
    email VARCHAR(254) NOT NULL UNIQUE CHECK (btrim(email) <> ''),
    phone VARCHAR(30) NOT NULL CHECK (btrim(phone) <> '')
);

CREATE TABLE customers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (btrim(name) <> ''),
    email VARCHAR(254) NOT NULL UNIQUE CHECK (btrim(email) <> ''),
    phone VARCHAR(30) NOT NULL CHECK (btrim(phone) <> ''),
    address VARCHAR(500) NOT NULL CHECK (btrim(address) <> ''),
    latitude NUMERIC(9, 6) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude NUMERIC(9, 6) NOT NULL CHECK (longitude BETWEEN -180 AND 180)
);

CREATE TABLE vehicles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (btrim(name) <> ''),
    capacity NUMERIC(12, 3) NOT NULL CHECK (capacity > 0),
    depot_id BIGINT NOT NULL,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    driver_id BIGINT UNIQUE,
    CONSTRAINT fk_vehicle_depot FOREIGN KEY (depot_id)
        REFERENCES depots(id) ON DELETE RESTRICT,
    CONSTRAINT fk_vehicle_driver FOREIGN KEY (driver_id)
        REFERENCES drivers(id) ON DELETE SET NULL
);

CREATE TABLE deliveries (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    quantity NUMERIC(12, 3) NOT NULL CHECK (quantity > 0),
    notes TEXT,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_delivery_customer FOREIGN KEY (customer_id)
        REFERENCES customers(id) ON DELETE RESTRICT
);

-- PostgreSQL does not automatically index referencing foreign-key columns.
-- driver_id already has an index supplied by its UNIQUE constraint.
CREATE INDEX idx_vehicles_depot_id ON vehicles(depot_id);
CREATE INDEX idx_deliveries_customer_id ON deliveries(customer_id);

COMMIT;
