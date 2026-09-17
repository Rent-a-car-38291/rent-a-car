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

To build or run the project, use one of the following tasks:

| Task              | Description       |
|-------------------|-------------------|
| `./gradlew test`  | Run the tests     |
| `./gradlew build` | Build the project |
| `./gradlew run`   | Run the server    |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

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
