CREATE TABLE optimization_jobs (
    id UUID PRIMARY KEY,
    scenario_id BIGINT NOT NULL,
    request_json TEXT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL,
    acknowledgment_json TEXT
);
CREATE INDEX idx_optimization_jobs_published ON optimization_jobs(published);
