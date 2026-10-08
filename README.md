# rent-a-car

This project was created using the [Ktor Project Generator](https://start.ktor.io).

Here are some useful links to get you started:

* [Ktor Documentation](https://ktor.io/docs/home.html)
* [Ktor GitHub page](https://github.com/ktorio/ktor)
* [Ktor Slack chat](https://app.slack.com/client/T09229ZC6/C0A974TJ9). [Request an invite](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up).

## Features

Here's a list of features included in this project:

| Name                                                                                  | Description                                                                        |
|---------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| [Content Negotiation](https://start.ktor.io/p/io.ktor/server-content-negotiation)     | Provides automatic content conversion according to Content-Type and Accept headers |
| [kotlinx.serialization](https://start.ktor.io/p/io.ktor/server-kotlinx-serialization) | Handles JSON serialization using kotlinx.serialization library                     |

## Building & Running

The project uses the [Kotlin Toolchain](https://github.com/JetBrains/amper). The `./kotlin` wrapper downloads it on the first run, so no install is needed. On Windows, use `kotlin.bat` instead of `./kotlin`.

| Command          | Description       |
|------------------|-------------------|
| `./kotlin test`  | Run the tests     |
| `./kotlin build` | Build the project |
| `./kotlin run`   | Run the server    |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

## Database

The cars are stored in PostgreSQL. Start a local database before running the server:

```bash
docker compose up -d
```

The connection defaults match `docker-compose.yml`. Override them with the environment variables `DATABASE_URL`, `DATABASE_USER` and `DATABASE_PASSWORD`. The tables are created and filled with sample cars when the server starts.

The tests don't need Docker: they start their own Postgres.

## Viewing diagrams (PlantUML)

Diagrams for this project live in [`docs/diagrams`](docs/diagrams) as `.puml` files, each with an exported `.png` next to it so GitHub can show it without a plugin. To view and edit them in your IDE, install the PlantUML plugin and a local renderer.

| Diagram                 | Shows                                                                         |
|-------------------------|-------------------------------------------------------------------------------|
| `use-cases`             | What each kind of user can do, taken from the issues                          |
| `domain-model`          | The domain as planned in the backlog, with fields and methods                 |
| `class-<area>`          | The classes as they are in `src/`, one diagram per area (`cars`, `accounts`)  |
| `components`            | How a request flows through plugins, routes and repositories                  |
| `sequence-<endpoint>`   | One flow per endpoint, with every status code                                 |

A PR that changes an endpoint or a class updates its diagram and PNG, and shows them in the PR description.

### 1. Install the plugin

- **IntelliJ IDEA**: Go to `Settings/Preferences > Plugins > Marketplace`, search for **PlantUML Integration**, and install it.

### 2. Install the renderer

PlantUML renders diagrams using Graphviz, which must be installed separately.

**macOS** (via [Homebrew](https://brew.sh)):

```bash
brew install graphviz
```

**Windows** (via [Chocolatey](https://chocolatey.org)):

```bash
choco install graphviz
```

Alternatively on Windows, download the installer from the [Graphviz website](https://graphviz.org/download/) and make sure to check "Add Graphviz to the system PATH" during installation.

After installing, restart your IDE and open a `.puml` file to see the rendered preview.

### Exporting images

After changing a `.puml`, export the PNGs again with the PlantUML command line (`brew install plantuml`) and commit them with it:

```bash
plantuml -tpng docs/diagrams/*.puml
```
