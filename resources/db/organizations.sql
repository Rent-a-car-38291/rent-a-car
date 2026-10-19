CREATE TABLE IF NOT EXISTS organizations (
    id   integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name text NOT NULL
);

-- A user is in an organization at most once; leaving the system or the organization removes the row.
CREATE TABLE IF NOT EXISTS memberships (
    organization_id integer NOT NULL REFERENCES organizations ON DELETE CASCADE,
    user_id         integer NOT NULL REFERENCES users ON DELETE CASCADE,
    role            text NOT NULL CHECK (role IN ('ADMIN', 'MEMBER')),
    PRIMARY KEY (organization_id, user_id)
);
