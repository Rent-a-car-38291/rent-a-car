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

## Trying the API (Swagger UI)

With the server running, open [http://localhost:8080/swagger](http://localhost:8080/swagger) to see every endpoint and try it with "Try it out". The page is described by [`resources/openapi/documentation.yaml`](resources/openapi/documentation.yaml), so add new endpoints there as well.

Swagger UI is only served in Ktor's development mode, which is on by default. Start the server with `KTOR_DEVELOPMENT=false` to turn it off.

## Viewing diagrams (PlantUML)

Diagrams for this project live in [`docs/diagrams`](docs/diagrams) as `.puml` files. To view them in your IDE, install the PlantUML plugin and a local renderer.

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
