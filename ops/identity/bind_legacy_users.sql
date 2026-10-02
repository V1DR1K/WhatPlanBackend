\set ON_ERROR_STOP on
\if :{?tomas_auth_uuid}
\else
  \echo 'Required: -v tomas_auth_uuid=<verified central UUID>'
  \quit 2
\endif
\if :{?avril_auth_uuid}
\else
  \echo 'Required: -v avril_auth_uuid=<verified central UUID>'
  \quit 2
\endif

BEGIN;
SELECT set_config('whatplan.identity.tomas', :'tomas_auth_uuid', true);
SELECT set_config('whatplan.identity.avril', :'avril_auth_uuid', true);

DO $$
DECLARE
    tomas_id uuid := current_setting('whatplan.identity.tomas')::uuid;
    avril_id uuid := current_setting('whatplan.identity.avril')::uuid;
    row_count integer;
BEGIN
    IF tomas_id = avril_id THEN
        RAISE EXCEPTION 'The two verified central identities must be distinct';
    END IF;

    IF (SELECT count(*) FROM users WHERE lower(username) = 'tomas') <> 1 THEN
        RAISE EXCEPTION 'Expected exactly one legacy tomas row';
    END IF;
    IF (SELECT count(*) FROM users WHERE lower(username) = 'avril') <> 1 THEN
        RAISE EXCEPTION 'Expected exactly one legacy avril row';
    END IF;

    PERFORM 1 FROM users WHERE lower(username) IN ('tomas', 'avril') FOR UPDATE;

    IF EXISTS (
        SELECT 1 FROM users
        WHERE lower(username) = 'tomas' AND auth_user_id IS NOT NULL AND auth_user_id <> tomas_id
    ) OR EXISTS (
        SELECT 1 FROM users
        WHERE lower(username) = 'avril' AND auth_user_id IS NOT NULL AND auth_user_id <> avril_id
    ) THEN
        RAISE EXCEPTION 'A legacy account is already linked to a different central identity';
    END IF;

    IF EXISTS (
        SELECT 1 FROM users
        WHERE auth_user_id IN (tomas_id, avril_id)
          AND lower(username) NOT IN ('tomas', 'avril')
    ) THEN
        RAISE EXCEPTION 'A verified central identity is already linked to another local account';
    END IF;

    UPDATE users SET auth_user_id = tomas_id
    WHERE lower(username) = 'tomas' AND (auth_user_id IS NULL OR auth_user_id = tomas_id);
    GET DIAGNOSTICS row_count = ROW_COUNT;
    IF row_count <> 1 THEN
        RAISE EXCEPTION 'Failed to bind exactly one legacy tomas account';
    END IF;

    UPDATE users SET auth_user_id = avril_id
    WHERE lower(username) = 'avril' AND (auth_user_id IS NULL OR auth_user_id = avril_id);
    GET DIAGNOSTICS row_count = ROW_COUNT;
    IF row_count <> 1 THEN
        RAISE EXCEPTION 'Failed to bind exactly one legacy avril account';
    END IF;
END $$;

COMMIT;
