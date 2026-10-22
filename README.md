# rent-a-car

[![Build](https://github.com/Rent-a-car-38291/rent-a-car/actions/workflows/build.yml/badge.svg)](https://github.com/Rent-a-car-38291/rent-a-car/actions/workflows/build.yml)

A car rental backend for the Avans course: browse a fleet of petrol, electric and hydrogen cars, and register, log in and manage your account.

Built with [Ktor](https://ktor.io) and Kotlin, and the [Kotlin Toolchain](https://github.com/JetBrains/amper) (Amper).

## Features

- Cars of three kinds: combustion (`ICE`), battery electric (`BEV`) and hydrogen (`FCEV`), with a sample fleet
- Register, log in and out, and update or delete your own account, with bearer tokens for protected routes
- Cars and accounts are stored in PostgreSQL
- Passwords are stored hashed

## Getting started

You need a JDK and Docker. The `./kotlin` wrapper downloads the toolchain on the first run, so there is nothing else to install. On Windows, use `kotlin.bat` instead of `./kotlin`.

Start the database, then the server:

```bash
docker compose up -d
./kotlin run
```

If the server starts successfully, you'll see:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

Try it:

```bash
curl http://localhost:8080/cars
```

| Command          | Description       |
|------------------|-------------------|
| `./kotlin run`   | Run the server    |
| `./kotlin test`  | Run the tests     |
| `./kotlin build` | Build the project |

The connection defaults match `docker-compose.yml`. Override them with the environment variables `DATABASE_URL`, `DATABASE_USER` and `DATABASE_PASSWORD`. The tables are created when the server starts, and the cars table is filled with sample cars when it is empty. Login tokens are not stored: after a restart everyone logs in again. The tests don't need Docker: they start their own Postgres.

## Trying the API (Swagger UI)

With the server running, open [http://localhost:8080/swagger](http://localhost:8080/swagger) to see every endpoint and try it with "Try it out". The page is described by [`resources/openapi/documentation.yaml`](resources/openapi/documentation.yaml), so add new endpoints there as well.

Swagger UI is only served in Ktor's development mode, which is on by default. Start the server with `KTOR_DEVELOPMENT=false` to turn it off.

## API

| Method   | Path                      | Description                    | Success | Errors                            |
| -------- | ------------------------- | ------------------------------ | ------- | --------------------------------- |
| `GET`    | `/cars`                   | List all cars                  | `200`   |                                   |
| `GET`    | `/cars/{id}/cost-per-km`  | Energy cost of one km          | `200`   | `400`, `404`                      |
| `GET`    | `/cars/{id}/tco`          | Total cost of ownership a year | `200`   | `400`, `401`, `403`, `404`        |
| `POST`   | `/accounts`               | Register an account            | `201`   | `400`, `409`                      |
| `POST`   | `/login`                  | Get a token                    | `200`   | `401`                             |
| `POST`   | `/logout`                 | Revoke the token you used      | `204`   | `401`                             |
| `PUT`    | `/accounts/{id}`          | Update your own name and email | `200`   | `400`, `401`, `403`, `404`, `409` |
| `PUT`    | `/accounts/{id}/password` | Change your own password       | `204`   | `400`, `401`, `403`, `404`        |
| `DELETE` | `/accounts/{id}`          | Delete your own account        | `204`   | `400`, `401`, `403`, `404`        |

A password needs 8 to 128 characters, and the email address must be unused. Everything under `/accounts/{id}` and `/logout` needs a login and only works on your own account. Routes that need a login take the token from `POST /login` in an `Authorization: Bearer <token>` header. Each endpoint has a [sequence diagram](docs/diagrams) with every status code.

```bash
curl -X POST localhost:8080/accounts -H 'Content-Type: application/json' \
     -d '{"name":"Sam","email":"sam@example.com","password":"correct horse"}'

curl -X POST localhost:8080/login -H 'Content-Type: application/json' \
     -d '{"email":"sam@example.com","password":"correct horse"}'
# {"token":"..."}
```

## Project layout

```
src/
├── Application.kt        wires the modules together
├── accounts/             users, login tokens, account routes
├── cars/                 the car hierarchy, repository and routes
└── database/             the PostgreSQL connection
resources/                server configuration and the SQL that creates the tables
test/                     mirrors src/
docs/diagrams/            PlantUML sources and exported PNGs
```

## Diagrams

Diagrams live in [`docs/diagrams`](docs/diagrams) as `.puml` files, each with an exported `.png` next to it so GitHub can show it without a plugin.

| Diagram                 | Shows                                                                         |
|-------------------------|-------------------------------------------------------------------------------|
| `use-cases`             | What each kind of user can do, taken from the issues                          |
| `domain-model`          | The domain as planned in the backlog, with fields and methods                 |
| `class-<area>`          | The classes as they are in `src/`, one diagram per area (`cars`, `accounts`)  |
| `components`            | How a request flows through plugins, routes and repositories                  |
| `sequence-<endpoint>`   | One flow per endpoint, with every status code                                 |

A PR that changes an endpoint or a class updates its diagram and PNG, and shows them in the PR description.

![Components](docs/diagrams/components.png)

### Viewing and editing

1. **Install the plugin.** In IntelliJ IDEA, go to `Settings/Preferences > Plugins > Marketplace`, search for **PlantUML Integration** and install it.
2. **Install Graphviz**, which PlantUML renders with:
   - macOS: `brew install graphviz`
   - Windows: `choco install graphviz`, or the installer from the [Graphviz website](https://graphviz.org/download/) with "Add Graphviz to the system PATH" checked.
3. Restart your IDE and open a `.puml` file to see the preview.

### Exporting images

After changing a `.puml`, export the PNGs again with the PlantUML command line (`brew install plantuml`) and commit them with it:

```bash
plantuml -tpng docs/diagrams/*.puml
```
