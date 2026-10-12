-- Identity ids only go up, so the id of a deleted user is never handed out again.
CREATE TABLE IF NOT EXISTS users (
    id            integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          text NOT NULL,
    email         text NOT NULL,
    password_hash text NOT NULL
);

-- One account per email address, ignoring case.
CREATE UNIQUE INDEX IF NOT EXISTS users_email_key ON users (lower(email));
