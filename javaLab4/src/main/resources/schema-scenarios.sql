-- Historical manual schema reference. Flyway V2 now manages these changes.
-- Do not execute this on a Flyway-managed database; see REST-ADVANCED.md for adoption.
BEGIN;

CREATE TABLE scenarios (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (btrim(name) <> '')
);

CREATE TABLE scenario_vehicles (
    scenario_id BIGINT NOT NULL REFERENCES scenarios(id) ON DELETE CASCADE,
    vehicle_id BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE RESTRICT,
    PRIMARY KEY (scenario_id, vehicle_id)
);

CREATE TABLE scenario_deliveries (
    scenario_id BIGINT NOT NULL REFERENCES scenarios(id) ON DELETE CASCADE,
    delivery_id BIGINT NOT NULL REFERENCES deliveries(id) ON DELETE RESTRICT,
    PRIMARY KEY (scenario_id, delivery_id)
);

CREATE INDEX idx_scenario_vehicles_vehicle_id ON scenario_vehicles(vehicle_id);
CREATE INDEX idx_scenario_deliveries_delivery_id ON scenario_deliveries(delivery_id);

COMMIT;
