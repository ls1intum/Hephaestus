# Database Migration

This project uses Liquibase for database schema version control and data migrations. [Liquibase](https://docs.liquibase.com/home.html) tracks changes to your database schema through changesets, which are stored in changelog files. This allows us to track changes to the database schema and reviewers can review the changes before they are applied to the database.

## Development Workflow with JPA

### Hibernate DDL Auto Configuration

During development, we use two different modes of database schema management. These can be configured in the `application.yml` file:

1. **Development Mode** (`spring.jpa.hibernate.ddl-auto=update`):
   - Hibernate automatically updates the database schema based on your JPA entities
   - Useful for rapid prototyping and development
   - Changes are applied directly to the database without migration scripts
   - **Warning**: Before creating a pull request, ensure that your changes are compatible with Liquibase migrations only

2. **Production Mode** (`spring.jpa.hibernate.ddl-auto=none`):
   - Hibernate does not modify the database schema
   - All schema changes must be made through Liquibase migrations
   - This is the only safe mode for production environments

```{hint}
The development mode is the default mode, but you can also develop using the production mode if you are comfortable with Liquibase and database migrations. In this case, make sure to generate changelogs as described in the [Creating New Migrations with Liquibase](#creating-new-migrations-with-liquibase) section.
```

### Development Process

1. **Initial Development**:
   - Use `ddl-auto: update` while developing new features.
   - Update your JPA entities as required.
   - Test changes using the automatic schema updates.

2. **Before Creating a Pull Request**:
   - Generate proper migrations by following the steps in [Creating New Migrations with Liquibase](#creating-new-migrations-with-liquibase).
   - Test your application to ensure it works correctly with migrations.
   - Verify that both fresh installations and updates work as expected.

3. **In the Pull Request**:
   - Verify the changelog file follows the [changelog conventions](#changelog-conventions) and has been committed to the branch
   - Describe the changes you made in the JPA entities in the description of the pull request

## Creating New Migrations with Liquibase

To create a new migration, follow these steps:

1. Run the following command in the Hephaestus folder to generate a migration:
   ```bash
   npm run db:changelog:diff
   ```
   This will create a new changelog file at `src/main/resources/db/changelog-new.xml`.
2. Review if the generated file reflects the schema changes as intended.
3. If the changes need adjustments, try to find suitable [Jakarta Persistence](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/package-summary) annotations before manually editing a changeset. 
4. When the changes are correct, adapt the changelog file according to the [changelog conventions](#changelog-conventions):
   - Rename the file to `<formatted-timestamp>_changelog.xml`. Use the ID of its changesets for the timestamp.
   - Replace the author name with your Github username.
   - Move the file to the `changelog` subdirectory.

The command `db:changelog:diff` performs the following operations:
- Stops an existing Postgres container of the Docker Compose stack
- Backs up the current database data
- Starts a new Postgres container
- Applies existing migrations using `liquibase:update`
- Generates a diff between the current database state and your JPA entities using `liquibase:diff`
- Restores the original database state

```{warning}
Occasionally, the command desynchronizes your Docker Compose stack. If launching the application server after running the command fails with a Postgres error, delete the existing Compose stack from your local Docker (Desktop) environment and start the server again.
```

## Changelog Conventions

- Changelogs consist of one or more changesets. Each changeset describes exactly one logical database change.
- All changelog files are stored in the `src/main/resources/db/changelog` directory.
- Changelog files should follow the naming convention `<formatted-timestamp>_changelog.xml`. Make sure the timestamp matches the first part of the ID of the included changesets.

## Important Liquibase Commands

When working with migrations, these are the key Maven commands used (executed within the application-server directory):

- `mvn liquibase:update`: Applies all pending changesets to bring the database up to date
- `mvn liquibase:diff`: Generates a changelog file containing differences between JPA entities and the current database state
- `mvn liquibase:rollback`: Rolls back the database to a previous state (requires specifying parameters).
- More commands can be found [here](https://docs.liquibase.com/commands/home.html)

## Best Practices

1. **Create Complex Types**: Use [Lombok](https://projectlombok.org/features/) and [Jakarta Persistence](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/package-summary) decorators and annotations to create complex properties and simplify the generation of migrations.

2. **Review Generated Changesets**: Always review the auto-generated changesets before committing them. They may need adjustments for optimal database performance.

3. **Test Migrations**: Always test migrations on the local and staging databases to ensure they work as expected and don't cause data loss.

4. **Version Control**: All changelog files should be committed to version control along with your application code.

## Common Issues

- Check that your JPA entities are properly annotated
- If the schema of your local postgres database is out of sync, you can reset it by deleting the `postgres-data` folder
- For best practices regarding column types, see the [database guidelines](../coding_design_guidelines/index.md#database)

## Manual Modifications

In exceptional cases, you may need to write migrations manually. Before doing so, make sure no decorators or annotations would fulfill your needs. Common scenarios include:

- Complex data transformations
- Performance-critical operations that need optimization
- Converting the type of an existing column

```{warning}
Try to avoid manually written SQL statements (`<sql>...</sql>`). A valid scenario for manual SQL is converting the row values when modifying the data type of a column, e.g. `varchar` to `oid`.
```

## Database Documentation (ERD Generation)

After making database schema changes, it's important to keep the database documentation up-to-date. The project includes an automated ERD (Entity Relationship Diagram) generation script.

### Updating Database Documentation

To generate or update the database ERD documentation, run:

```bash
npm run db:erd:generate
```

This command:

- Starts a PostgreSQL container using Docker Compose
- Waits for the database to be ready
- Applies all Liquibase migrations to ensure the schema is current
- Runs a Python script to generate the Mermaid ERD from the database schema
- Updates the documentation file at `docs/dev/database-schema.mmd`

The generated ERD file is used in the database documentation and provides a visual representation of:

- All database tables and their columns
- Primary and foreign key relationships
- Data types and constraints
- Entity relationships (one-to-one, one-to-many, many-to-many)

```{note}
The ERD generation is also part of the CI/CD pipeline and will automatically check if the documentation is up-to-date when you create a pull request. If the ERD is out of sync with your schema changes, the build will prompt you to update it.
```

### When to Update the ERD

- After creating new Liquibase migrations
- Before creating a pull request with database changes
- When the CI pipeline indicates the ERD is out of date

```{hint}
The ERD generation script uses the actual database schema (after migrations) rather than JPA entities, ensuring the documentation reflects the true database state.
```
