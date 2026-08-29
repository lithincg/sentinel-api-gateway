ALTER TABLE api_keys ADD COLUMN lookup_hash VARCHAR(64);

CREATE UNIQUE INDEX idx_api_key_lookup_hash ON api_keys(lookup_hash);
