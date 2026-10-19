-- Identity ids only go up, so the id of a deleted user is never handed out again.
CREATE TABLE IF NOT EXISTS users (
    id            integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          text NOT NULL,
    email         text NOT NULL,
    password_hash text NOT NULL
);

-- One account per email address, ignoring case.
CREATE UNIQUE INDEX IF NOT EXISTS users_email_key ON users (lower(email));

-- Reserved owner for the sample fleet; its random password hash has no known password.
INSERT INTO users (id, name, email, password_hash) OVERRIDING SYSTEM VALUE
VALUES (-1, 'Sample fleet', 'sample-fleet@rent-a-car.invalid', 'ej0M8Ts0bX5FqZlbL0eW7A==:tSdgcA12/mS+4UQvxObL5qo/ieZi7VUbZQubxBaG6hg=')
ON CONFLICT DO NOTHING;
