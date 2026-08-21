ALTER TABLE users ALTER COLUMN username TYPE varchar(80);
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE users ADD COLUMN auth_user_id uuid;
ALTER TABLE users ADD CONSTRAINT uk_users_auth_user_id UNIQUE (auth_user_id);
UPDATE users SET password_hash = NULL;
