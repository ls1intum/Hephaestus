============================
Coding and Design Guidelines
============================

These guidelines are intended to help maintain a consistent coding style and design across the Hephaestus codebase. We distinguish between these main areas:

- Performance Optimizations
- Database Design
- Server / API Design
- Client Design

Performance Optimization
------------------------


Database Queries
~~~~~~~~~~~~~~~~

When dealing with the database, it is important to optimize the queries to minimize the number of database calls and the amount of data retrieved. Use the `@Query` annotation to write custom queries in the repository classes:

1. **Only retrieve necessary data**: Filtering and smart data formatting as part of the query can help reduce the amount of data retrieved from the database.
2. **Use pagination**: When retrieving a large number of records, use pagination to limit the number of records retrieved in a single query.



Database Design
---------------

This section contains guidelines to optimize the database schema and queries for performance and maintainability. Hephaestus uses a PostgreSQL database, which is accessed through the JPA (Java Persistence API) with Hibernate.


Entity Design
~~~~~~~~~~~~~

To define a new entity, create a new class with the `@Entity` annotation. The class should have a no-argument constructor and a primary key field annotated with `@Id`. The primary key field should be of type `Long` and should be auto-generated. For example:

.. code-block:: java

    @Entity
    @Table(name = "example")
    public class ExampleClass {
    
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        
        // Other fields and methods
    }


Server / API Design
-------------------

This section contains guidelines for designing the server and API endpoints. Hephaestus uses Spring Boot to create a RESTful API, which is secured using JSON Web Tokens (JWT).


Optionals
~~~~~~~~~

Throughout the JAVA codebase, use `Optionals` instead of `null` to represent values that may be absent. This helps to avoid `NullPointerExceptions` and makes the code more readable. For example:

.. code-block:: java

    // Getting an entity in a repository class
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

Data Transfer Objects (DTOs)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

When designing API endpoints, use Data Transfer Objects (DTOs) to define the structure of the data exchanged between the client and the server. DTOs help to decouple the internal entity structure from the external API structure and provide a clear contract for the data exchanged.

The client should receive data in the form of DTOs, which should be typed via the generated OpenAPI specification and modules.

Example (Server):

.. code-block:: java

    public record ExampleDTO(String name) {
        public static ExampleDTO fromEntity(ExampleClass example) {
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

    // Alternatively you can return DTOs directly from the repository. This is generally only useful for extremely specific requests.
    @Repository
    public interface ExampleRepository extends JpaRepository<ExampleClass, Long> {
    
        @Query("""
            SELECT new com.example.ExampleDTO(e.name) FROM ExampleClass e WHERE e.name = :name
            """)
        Optional<ExampleDTO> findByName(String name);

        // ...
    }



Example (Client):

.. code-block:: typescript

    // This is auto-generated from the OpenAPI specification
    export interface ExampleDTO {
        name: string;
    }

    // In an Angular Component
    @Component({
        selector: 'app-example',
        templateUrl: './example.component.html',
        styleUrls: ['./example.component.css']
    })
    export class ExampleComponent {
        // type-safe access to the server
        exampleService = inject(ExampleService);

        // Use Tanstack Query to fetch the actual data
        // The data now inherits the DTO structure and can be accessed in a type-safe manner
        example = injectQuery(() => ({
            queryKey: ['example'],
            queryFn: async () => lastValueFrom(this.exampleService.getExampleByName('example')),
        }));
    }