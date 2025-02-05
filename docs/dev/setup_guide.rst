===========
Setup Guide
===========

Hephaestus supports both development and production environment setups. This setup guide focuses on the development environment setup. The production environment setup is covered in the `Production Setup Guide <production_setup.html>`_.

Note that depending on the operating system you are using, the setup steps may vary or require additional steps. For that reason, we try to keep the setup steps as universal as possible. If you encounter any issues during the setup, please refer to the `Troubleshooting <troubleshooting.html>`_ section.

Operating System Setup
----------------------

Before you can build Hephaestus, you must install and configure the following dependencies/tools on your machine:

1. `Java JDK <https://www.oracle.com/java/technologies/javase-downloads.html>`__:
   We use Java (JDK 21) to develop and run the Artemis application
   server, which is based on `Spring
   Boot <http://projects.spring.io/spring-boot>`__.
2. `PostgreSQL 17 <https://www.postgresql.org/>`_: Hephaestus uses Hibernate to store entities in an SQL database and Liquibase to automatically apply schema transformations when updating Artemis.
3. `Node.js <https://nodejs.org/en/download>`__: We use Node LTS (>=22.10.0 < 23) to compile
   and run the client Angular application. Depending on your system, you
   can install Node either from source or as a pre-packaged bundle.
4. `Npm <https://nodejs.org/en/download>`__: We use Npm (>=10.8.0) to
   manage client side dependencies. Npm is typically bundled with Node.js,
   but can also be installed separately.
5. `Python <https://www.python.org/downloads/>`__: We use Python (>=3.9) to develop and run the intelligence service.

IDE Setup
---------

The first step is to set up an Integrated Development Environment (IDE) for development. We recommend using `Visual Studio Code <https://code.visualstudio.com/>`_ (VS Code) as it is lightweight, fast, and has a lot of extensions that can help you with development.
Alternatively, you can use JetBrain's IDEs depending on part of Hephaestus you are working with: 

- `IntelliJ <https://www.jetbrains.com/idea/>`_ for the *application-server* (Spring Boot - Java), 
- `WebStorm <https://www.jetbrains.com/webstorm/>`_ for the *web-app* (Angular - Typescript) and 
- `PyCharm <https://www.jetbrains.com/pycharm/>`_ for the *intelligence-service* (Python).

In case you are using **VS Code**, we furthermore recommend to install the following extensions from the marketplace to enhance your development experience:

- `Java Extension Pack <https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack>`_ for Java development,
- `Language Support for Java(TM) by Red Hat <https://marketplace.visualstudio.com/items?itemName=redhat.java>`_ for Spring Boot development,
- `Maven for Java <https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-maven>`_ for Maven support,
- `Python <https://marketplace.visualstudio.com/items?itemName=ms-python.python>`_ for Python development.
- `Angular Language Service <https://marketplace.visualstudio.com/items?itemName=Angular.ng-template>`_ for Angular development,
- `Tailwind CSS IntelliSense <https://marketplace.visualstudio.com/items?itemName=bradlc.vscode-tailwindcss>`_ for Tailwind CSS development,
- `Prettier - Code formatter <https://marketplace.visualstudio.com/items?itemName=esbenp.prettier-vscode>`_ for code formatting.


Docker Setup
------------

This section describes how to set up a Docker environment for development. This is a prerequisite for running the Hephaestus system locally, please follow the steps below to install Docker Desktop on your machine:

1. **Install Docker Desktop**: Download and install `Docker Desktop <https://www.docker.com/products/docker-desktop>`_ for your operating system.
2. **Start Docker Desktop**: Once installed, start Docker Desktop. You should see a Docker icon in your system tray.
3. **Verify Installation**: Open a terminal and run the following command to verify that Docker is installed correctly:

.. code-block:: bash

    docker --version

If Docker is installed correctly, you should see the version number of the Docker engine installed on your machine.


Server Setup
------------

Note that the following steps vary depending on your choice of IDE. This guide takes a terminal-based approach optimized for developers who prefer working with the command line. If you are using an IDE like IntelliJ, you may find it easier to use the built-in tools instead.

Maven Profiles and Configuration Setup
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Maven profiles help you manage different build configurations. Hephaestus currently uses the following profiles:

- **local**: Development profile for local development
- **prod**: Production profile for deployment
- **spec**: Profile for running the test suite (i.e. Github Actions)

For development, we use the ``local``-profile, which should be set up with local development settings:

1. **Maven Profile Selection**: To activate the development profile, you can either:

    - Add ``-Pdev`` to your Maven commands
    - Set it permanently in your IDE's run configuration (IntelliJ)
    - Note: The default profile is ``local``.

2. **Local Configuration**: Create a file named ``application-local.yml`` in ``application-server/src/main/resources/`` with your local settings, for example:

.. code-block:: yaml

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

This configuration file is git-ignored and allows you to override default settings defined in ``application.yml``.

.. note::

    The ``application-local.yml`` file is used to store local settings that should never be committed to the repository. Make sure to keep sensitive information like API keys out of version control.

    In case you want to create your own configuration profile, you can create a new file named ``application-<profile>.yml`` and activate it by setting the ``spring.profiles.active`` property in your IDE's run configuration accordingly or via the command line. Always make sure to ignore the new file in the ``.gitignore``.


Running the server
~~~~~~~~~~~~~~~~~~

To run the server, follow these steps:

1. **Start Docker**: Make sure Docker Desktop is running.
2. **Run Maven**: Open a terminal and navigate to the ``application-server`` directory. Run the following command to build the project:

.. code-block:: bash

    mvn spring-boot:run

3. **Access the Application**: Once the server is running, you can access the application by making requests to `http://localhost:8080`. You can use any REST client like Postman or cURL to interact with the server.  


Client Setup
------------

The client setup is straightforward and requires only a few steps:

1. **Install Dependencies**: Open a terminal and navigate to the ``web-app`` directory. Run the following command to install the required dependencies:

.. code-block:: bash

    npm install

2. **Start the Client**: Run the following command to start the client application:

.. code-block:: bash

    npm start

3. **Access the Application**: Once the client is running, you can access the application by opening a browser and navigating to `http://localhost:4200`. The client application should be up and running, allowing you to interact with the server.

