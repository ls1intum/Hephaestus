# Getting Started

### Local development

The Spring Boot Maven Plugin supports running your application with a local development environment. To do this, you can use the `spring-boot:run` goal:

```shell
mvn spring-boot:run
```

This will start your application in a local development environment. You can access the application at `http://localhost:8080`.

Setting environment variables works through profile-based `application.yml` files. For local development, create a `application-local.yml` file overwriting the original properties. See [Using the Plugin :: Spring Boot](https://docs.spring.io/spring-boot/maven-plugin/using.html#using.overriding-command-line) for more information.


### Keycloak Development Test Users

If you started server for development it will import the example config for Keycloak from `keycloak-hephaestus-realm-example-config.json`.

#### Admin Account

- Username: `admin`
- Password: `admin`

Has the `admin` role.

#### Test User Account

- Username: `testuser`
- Password: `testuser`

Does not have the `admin` role.


### Reference Documentation

For further reference, please consider the following sections:

-   [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
-   [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/docs/3.3.2/maven-plugin/reference/html/)
-   [Create an OCI image](https://docs.spring.io/spring-boot/docs/3.3.2/maven-plugin/reference/html/#build-image)
-   [Docker Compose Support](https://docs.spring.io/spring-boot/docs/3.3.2/reference/htmlsingle/index.html#features.docker-compose)
-   [Spring Boot DevTools](https://docs.spring.io/spring-boot/docs/3.3.2/reference/htmlsingle/index.html#using.devtools)
-   [Spring Web](https://docs.spring.io/spring-boot/docs/3.3.2/reference/htmlsingle/index.html#web)
-   [Spring Security](https://docs.spring.io/spring-boot/docs/3.3.2/reference/htmlsingle/index.html#web.security)
-   [Spring Data JPA](https://docs.spring.io/spring-boot/docs/3.3.2/reference/htmlsingle/index.html#data.sql.jpa-and-spring-data)
-   [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/3.3.2/reference/htmlsingle/index.html#actuator)
-   [Liquibase Migration](https://docs.spring.io/spring-boot/docs/3.3.2/reference/htmlsingle/index.html#howto.data-initialization.migration-tool.liquibase)
-   [OAuth2 Client](https://docs.spring.io/spring-boot/docs/3.3.2/reference/htmlsingle/index.html#web.security.oauth2.client)
-   [OAuth2 Resource Server](https://docs.spring.io/spring-boot/docs/3.3.2/reference/htmlsingle/index.html#web.security.oauth2.server)

### Guides

The following guides illustrate how to use some features concretely:

-   [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
-   [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
-   [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
-   [Securing a Web Application](https://spring.io/guides/gs/securing-web/)
-   [Spring Boot and OAuth2](https://spring.io/guides/tutorials/spring-boot-oauth2/)
-   [Authenticating a User with LDAP](https://spring.io/guides/gs/authenticating-ldap/)
-   [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
-   [Building a RESTful Web Service with Spring Boot Actuator](https://spring.io/guides/gs/actuator-service/)

### Docker Compose support

This project contains a Docker Compose file named `compose.yaml`.
In this file, the following services have been defined:

-   postgres: [`postgres:16-alpine`](https://hub.docker.com/_/postgres)

Please review the tags of the used images and set them to the same as you're running in production.

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.
