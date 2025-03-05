# Server

This section contains guidelines for designing the server and API endpoints. Hephaestus uses Spring Boot to create a RESTful API, which is secured using JSON Web Tokens (JWT).

## Folder Structure

The Java server (commonly referred to as `application-server`) can be found under `server/application-server`. This folder contains the following main folders in `src/main`:

- **resources**: Contains the compile-time configuration files for the server. 
  - Most importantly, this includes the environment variables defined in `application-{env}.yml` files. Variables can be loaded according to the current environment, in development, the `application.yml` and `application-local.yml` files are used.
  - The **/db**-folder contains the database schema and migrations. Refer to the Liquibase sections for more information.
- **java**: Contains the Java source code for the server. Folders are organized by feature/functionality.
  - **/config**: Contains the configuration classes for the server and external services.
  - **/core**: Core elements of the server, such as exception and utility classes.
  - **/gitprovider**: Database access and representation of the Github API and potential other git providers.
  - **/intelligenceservice**: OpenAPI auto-generated code for the intelligence service. Used to communicate with the intelligence service and get type-safe responses.
  - **/leaderboard**: Logic related to the leaderboard and correlated gamification features.
  - **/mentor**: Database access and entities related to the mentor feature.
  - **/meta**: General meta-data about the server accessible for other components and the client to consume. Mainly contains non-feature specific information, such as environment variables required for the client.
  - **/syncing**: Logic related to the syncing of data between the server and the git providers.
  - **/workspaces**: Classes related to workspace access and management.

## Optionals

Throughout the Java codebase, use `Optionals` instead of `null` to represent values that may be absent. This helps to avoid `NullPointerExceptions` and makes the code more readable. In many cases, Spring Boot returns them automatically. For example:

```java
// Finding a database row via the repository class
@Repository
public interface ExampleRepository extends JpaRepository<ExampleClass, Long> {

    Optional<ExampleClass> findByName(String name);

    // ...
}

// Returning optional values in a service class
@Service
public class ExampleService {

    public Optional<ExampleClass> getExampleByName(String name) {
        Optional<ExampleClass> example = exampleRepository.findByName(name);
        if (example.isEmpty()) {
            return Optional.empty();
        }
        ExampleClass exampleClass = example.get();
        // Perform some operations...
        return exampleClass;
    }
}
```

## Data Transfer Objects (DTOs)

When designing API endpoints, use Data Transfer Objects (DTOs) to define the structure of the data exchanged between the client and the server. DTOs help to decouple the internal entity structure from the external API structure and provide a clear contract for the data exchanged.

The client should receive data in the form of DTOs, which should be typed via the generated OpenAPI specification and modules (see [Client](./client.md#client-side-data-handling) version). Example:

```java
public record ExampleDTO(String name) {
    public static ExampleDTO fromExampleClass(ExampleClass example) {
        return new ExampleDTO(example.getName());
    }
}

@RestController
@RequestMapping("/api/example")
public class ExampleController {

    @GetMapping("/{id}")
    public ResponseEntity<ExampleDTO> getExampleByName(@PathVariable String name) {
        Optional<ExampleClass> example = exampleService.getExampleByName(name);
        if (example.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ExampleDTO exampleDTO = ExampleDTO.fromEntity(example.get());
        return ResponseEntity.ok(exampleDTO);
    }

}

// Alternatively you can return DTOs directly from the repository. 
// This is generally only useful for extremely specific requests.
@Repository
public interface ExampleRepository extends JpaRepository<ExampleClass, Long> {

    @Query("""
        SELECT new com.example.ExampleDTO(e.name) 
        FROM ExampleClass e 
        WHERE e.name = :name
        """)
    Optional<ExampleDTO> findByName(String name);

    // ...
}
```

**General Guidelines for DTOs:**

- **Immutable Java Records**: Use Java's `record` syntax to guarantee immutability. While Java records preclude inheritance, resulting in potential duplication, this is considered acceptable in the context of DTOs to ensure data integrity and simplicity.
- **Primitive data types and composition**: DTOs should strictly encapsulate primitive data types, their corresponding wrapper classes, enums, or compositions of other DTOs. This exclusion of entity objects from DTOs ensures that data remains decoupled from the database entities, facilitating a cleaner and more secure data transfer mechanism.
- **Minimum necessary data**: Adhere to the principle of including only the minimal data required by the client within DTOs. This practice reduces the overall data footprint, enhances performance, and mitigates the risk of inadvertently exposing unnecessary or sensitive data.
- **Single responsibility principle**: Each DTO should be dedicated to a specific task or subset of data. Avoid the temptation to reuse DTOs across different data payloads unless the data is identical. This approach maintains clarity and purpose within the data transfer objects.
- **Simplicity over complexity**: Refrain from embedding methods or business logic within DTOs. Their role is to serve as straightforward data carriers without additional functionalities that could complicate their structure or purpose.

## Dependency Injection

- We prefer field injection using `@Autowired` over constructor injection. This avoids bloated constructors with many parameters and makes dependency injection more explicit through method parameters.
- This means that it is important to manually check for and resolve circular dependencies. In general, the less dependencies a class has, the easier it is to reason about its behavior and the more maintainable it is.

## Logging

- Initialize a new logger of type `Logger` for each class.
- Prefer structured logging (e.g. using `{}` placeholders) over traditional logging.
- Keep logging at the DEBUG level for development purposes and minimize the amount of logging in production. Preferably only log errors.
- If you need to customize the logging level for a specific class or external dependency, you can do so by setting the logging level in the `application-local.yml` file (see example below).

Example for structured logging:

```java
// ExampleService.java
@Service
public class ExampleService {
    // Make sure to use the class name as the logger name
    private static final Logger logger = LoggerFactory.getLogger(ExampleService.class);

    public void doSomething() {
        logger.info("User {} logged in", user.getName());
    }
}
```

Example for custom logging levels:

```yaml
# application-local.yml
logging:
  level:
    # Log all errors from the Github API
    org.kohsuke.github.GithubClient: ERROR
    # Log all errors from the Slack API
    com.slack.api: ERROR
```

## Environment Variables

In Spring Boot, environment variables are found in the `application-{env}.yml` files. Which files are loaded depends on the current environment, which can be set via the `SPRING_PROFILES_ACTIVE` execution parameter or via CLI arguments.

The following `env` profiles are important:

- **local**: Used for local development. This file is not committed to the repository and should contain all your secrets, such as API keys or tokens. You can use the `application.yml` file as a template and fill in the missing values.
- **specs**: Used for the staging environment, such as Github Action executions. This file usually does not need to be touched.
- **prod**: Used for the production environment. This file is committed to the repository and contains the production-relevant default values. These are overwritten by the actual environment variables of the deployment server.

Per default, Spring Boot will load the `application.yml` file first and then the `application-{env}.yml` file. This means that values can be overwritten using more specific profiles. Omit variables to avoid this behavior.

On UNIX-based systems, environment variables can also be set via the `export` command. This is generally only recommended for testing purposes as it makes it hard for other developers to reproduce your environment. For example:

```bash
# Set the active profile to local
export SPRING_PROFILES_ACTIVE="local"
# Set the Github API token
export github.auth.token="..."
```

## Best Practices

- **Avoid Transactional**: Use the `@Transactional` annotation sparingly. It is useful for operations that span multiple database queries, but is very performance-intensive and generally can be solved differently. Good read: https://codete.com/blog/5-common-spring-transactional-pitfalls/
- **Format using Prettier**: We use Prettier as code formatter. The npm setup of the main Hephaestus folder can be used to format the Java code. The commands `npm run format:java:check` and `npm run format:java:write` can be used to check and format the Java code, respectively. Make sure to run these scripts regularly to avoid formatting issues. 