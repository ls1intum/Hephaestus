This section contains guidelines to optimize the database schema and queries for performance and maintainability. Hephaestus uses a PostgreSQL database, which is accessed through the JPA (Java Persistence API) with Hibernate.

### Database Queries

When dealing with the database, it is important to optimize the queries to minimize the number of database calls and the amount of data retrieved. Use the `@Query` annotation to write custom queries in the repository classes and follow the following principles:

1. **Only retrieve necessary data**: Filtering and smart data formatting as part of the query can help reduce the amount of data retrieved from the database.
2. **Use pagination**: When retrieving a large number of records, use pagination to limit the number of records retrieved in a single query.
3. **Follow the naming conventions**: To avoid writing unnecessary custom queries, make use of the pre-defined keywords in the method name instead. You can find a list of reserved words in the [Spring Data JPA documentation](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html).

### Entity Design

To define a new entity, create a new class with the `@Entity` annotation. The class should have a no-argument constructor and a primary key field annotated with `@Id`. The primary key field should be of type `Long` and should be auto-generated. For example:

```java 
@Entity
@Table(name = "example")
public class ExampleClass {

    @Id
    private Long id;

    // Other fields and methods
}
```

### Entity Fields

- Use `@NonNull` to indicate that a field is required. Generally, this is a very desirable property and should be used unless there is a specific reason to allow the field to be `null`.
- Use `@Id` for the primary ID key field. Every entity should have such a field.
- If `String` fields can contain more than 255 characters, use `@Column(columnDefinition = "TEXT")` to indicate that the field should be stored as a TEXT column in the database. This is particularly important whenever fields are filled by an external source (e.g. the Github API).

### Entity Relationships

- Use `@ToString.Exclude` to exclude fields from the `toString`. This is particularly useful for relationships to avoid circular references. 