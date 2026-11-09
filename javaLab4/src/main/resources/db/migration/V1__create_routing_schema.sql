-- Initial routing schema. Flyway manages the transaction and schema history.
CREATE TABLE depots (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (trim(name) <> ''),
    latitude NUMERIC(9, 6) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude NUMERIC(9, 6) NOT NULL CHECK (longitude BETWEEN -180 AND 180)
);

CREATE TABLE drivers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (trim(name) <> ''),
    email VARCHAR(254) NOT NULL UNIQUE CHECK (trim(email) <> ''),
    phone VARCHAR(30) NOT NULL CHECK (trim(phone) <> '')
);

CREATE TABLE customers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (trim(name) <> ''),
    email VARCHAR(254) NOT NULL UNIQUE CHECK (trim(email) <> ''),
    phone VARCHAR(30) NOT NULL CHECK (trim(phone) <> ''),
    address VARCHAR(500) NOT NULL CHECK (trim(address) <> ''),
    latitude NUMERIC(9, 6) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude NUMERIC(9, 6) NOT NULL CHECK (longitude BETWEEN -180 AND 180)
);

CREATE TABLE vehicles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (trim(name) <> ''),
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

CREATE INDEX idx_vehicles_depot_id ON vehicles(depot_id);
CREATE INDEX idx_deliveries_customer_id ON deliveries(customer_id);
