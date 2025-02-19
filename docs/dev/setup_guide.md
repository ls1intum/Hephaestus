# Setup Guide

Hephaestus supports both development and production environment setups. This setup guide focuses on the development environment setup. The production environment setup is covered in the [Production Setup Guide](../admin/production_setup.md).

## Prerequisites

Before you can build Hephaestus, you must install and configure the following dependencies/tools on your machine:

1. [Java JDK](https://www.oracle.com/java/technologies/javase-downloads.html): We use Java (JDK 21) to develop and run the Hephaestus Application Server, which is based on [Spring Boot](http://projects.spring.io/spring-boot).
2. [PostgreSQL 16](https://www.postgresql.org/): Hephaestus uses [Hibernate](https://hibernate.org/) to store entities in an SQL database and [Liquibase](https://www.liquibase.com/) to automatically apply schema transformations when updating the server. (No installation required since we use Docker)
3. [Node.js](https://nodejs.org/en/download): We use Node LTS (>=22.10.0 < 23) to compile and run our Hephaestus Application Client based on [Angular](https://angular.dev/). Depending on your system, you can install Node either from source or as a pre-packaged bundle.
4. [Npm](https://nodejs.org/en/download): We use Npm (>=10.8.0) to manage client side dependencies. Npm is typically bundled with Node.js, but can also be installed separately.
5. [Python](https://www.python.org/downloads/): We use Python (>=3.12) to develop and run our Hephaestus Intelligence Service.
6. [Docker Desktop](https://www.docker.com/products/docker-desktop): We use Docker to containerize our application and run it in a consistent environment across different machines as well as for spinning up a PostgreSQL database, [Keycloak](https://www.keycloak.org/), and [NATS](https://nats.io/) server for local development.
7. (Optional) [NATS-cli](https://github.com/nats-io/natscli) for interacting with the [NATS](https://nats.io/) server. Comes in handy for debugging and testing when working with the NATS server.

## IDE Setup

The first step is to set up an Integrated Development Environment (IDE) for development. We recommend using [Visual Studio Code (VSCode)](https://code.visualstudio.com/) as it is lightweight, fast, and has a lot of extensions that can help you with development.

We provide a `project.code-workspace` file in the root directory of the repository that you can open in VSCode to get started. We recommend to install the workspace recommended extensions by first searching for `@recommended` in the Extensions view (`CMD/Ctrl+Shift+X`), and then installing the Workspace Recommendations extension.

The recommended extensions include:

- [Java Extension Pack](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack) for Java development
- [Language Support for Java(TM) by Red Hat](https://marketplace.visualstudio.com/items?itemName=redhat.java) for Spring Boot development
- [Maven for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-maven) for Maven support
- [Python](https://marketplace.visualstudio.com/items?itemName=ms-python.python) for Python development
- [Angular Language Service](https://marketplace.visualstudio.com/items?itemName=Angular.ng-template) for Angular development
- [Tailwind CSS IntelliSense](https://marketplace.visualstudio.com/items?itemName=bradlc.vscode-tailwindcss) for Tailwind CSS development
- [Prettier - Code formatter](https://marketplace.visualstudio.com/items?itemName=esbenp.prettier-vscode) for code formatting

Alternatively, you can use JetBrain's IDEs depending on part of Hephaestus you are working with:

- [IntelliJ](https://www.jetbrains.com/idea/) for the _application-server_ (Spring Boot - Java),
- [WebStorm](https://www.jetbrains.com/webstorm/) for the _webapp_ (Angular - Typescript) and
- [PyCharm](https://www.jetbrains.com/pycharm/) for the _intelligence-service_ (Python).

## Application Server Setup

Note that the following steps vary depending on your choice of IDE. This guide takes a terminal-based approach optimized for developers who prefer working with the command line. If you are using an IDE like IntelliJ, you may find it easier to use the built-in tools instead.

### Maven Profiles and Configuration Setup

Maven profiles help you manage different build configurations. Hephaestus currently uses the following profiles:

- **local**: Development profile for local development
- **prod**: Production profile for deployment
- **spec**: Profile for running the test suite (i.e. Github Actions)

For development, we use the `local`-profile, which should be set up with local development settings:

1. **Maven Profile Selection**: To activate the development profile, you can either:

   - Add `-Pdev` to your Maven commands
   - Set it permanently in your IDE's run configuration (IntelliJ)
   - Note: The default profile is `local`.

2. **Local Configuration**: Create a file named `application-local.yml` in `application-server/src/main/resources/` with your local settings, for example:

```yaml
# NATS configuration
nats:
  # Whether to enable data fetching through NATS
  enabled: true
  # Fetching timeframe in days
  timeframe: 1

# Github API Monitoring configuration
monitoring:
  # Whether to run the monitoring on startup
  run-on-startup: true
  # Fetching timeframe in days
  timeframe: 1
  # Cooldown in minutes before running the monitoring again
  sync-cooldown-in-minutes: 60
```

This configuration file is ignored by git and allows you to override default settings defined in `application.yml`.

```{attention}
The ``application-local.yml`` file is used to store local settings that should never be committed to the repository. Make sure to keep sensitive information like API keys out of version control.
```

### Running the server

To run the server, follow these steps:

1. **Start Docker**: Make sure Docker Desktop is running.
2. **Run Maven**: Open a terminal in the `application-server` directory. Run the following command to build the project:

```bash
mvn spring-boot:run
```

3. **Access the Application**: Once the server is running, you can access the application by making requests to `http://localhost:8080`. You can use any REST client like Postman or cURL to interact with the server or use the OpenAPI Swagger UI by navigating to `http://localhost:8080/swagger-ui/index.html`.

### Keycloak Setup

TODO Setup GitHub Identity Provider

## Application Client Setup

The client setup is straightforward and requires only a few steps:

1. **Install Dependencies**: Open a terminal in the `webapp` directory. Run the following command to install the required dependencies:

```bash
npm install
```

2. **Start the Client**: Run the following command to start the application client:

```bash
npm start
```

3. **Access the Application**: Once the client is running, you can access the application by opening a browser and navigating to `http://localhost:4200`. The application client should be up and running, allowing you to interact with the server.

## Intelligence Service Setup

TODO Setup Intelligence Service