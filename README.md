# rent-a-car

[![Build](https://github.com/Rent-a-car-38291/rent-a-car/actions/workflows/build.yml/badge.svg)](https://github.com/Rent-a-car-38291/rent-a-car/actions/workflows/build.yml)

A car rental backend for the Avans course: browse a fleet of petrol, electric and hydrogen cars, and register and log in with an account.

Built with [Ktor](https://ktor.io) and Kotlin, and the [Kotlin Toolchain](https://github.com/JetBrains/amper) (Amper).

## Features

- Cars of three kinds: combustion (`ICE`), battery electric (`BEV`) and hydrogen (`FCEV`), with a sample fleet
- Register and log in, with bearer tokens for protected routes
- Passwords are stored hashed

## Getting started

You need a JDK. The `./kotlin` wrapper downloads the toolchain on the first run, so there is nothing else to install. On Windows, use `kotlin.bat` instead of `./kotlin`.

```bash
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

Cars, accounts and login tokens are kept in memory: after a restart the sample cars are back and everyone has to register and log in again.

## API

| Method | Path        | Description         | Success | Errors       |
|--------|-------------|---------------------|---------|--------------|
| `GET`  | `/cars`     | List all cars       | `200`   |              |
| `POST` | `/accounts` | Register an account | `201`   | `400`, `409` |
| `POST` | `/login`    | Get a token         | `200`   | `401`        |

A password needs at least 8 characters, and the email address must be unused. Routes that need a login take the token from `POST /login` in an `Authorization: Bearer <token>` header. Each endpoint has a [sequence diagram](docs/diagrams) with every status code.

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
└── cars/                 the car hierarchy, repository and routes
resources/                server configuration
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
