CREATE TABLE app_secrets (
    name VARCHAR(100) PRIMARY KEY,
    value TEXT NOT NULL
);
-- Shared high-entropy key survives instance restarts without entering source or logs.
INSERT INTO app_secrets(name,value)
VALUES ('jwt', gen_random_uuid()::text || gen_random_uuid()::text || gen_random_uuid()::text);
