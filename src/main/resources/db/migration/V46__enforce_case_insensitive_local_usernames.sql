-- Prevent case variants of a legacy username from bypassing the verified-identity
-- collision check in LocalUserProvisioner. Existing conflicts must be resolved
-- explicitly before this migration is applied.
CREATE UNIQUE INDEX uq_users_username_lower ON users (lower(username));
