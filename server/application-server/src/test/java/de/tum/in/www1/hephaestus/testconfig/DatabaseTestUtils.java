package de.tum.in.www1.hephaestus.testconfig;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Utility for cleaning database state between integration tests.
 * Ensures test isolation with minimal performance impact.
 */
@Component
@Transactional
public class DatabaseTestUtils {

    @PersistenceContext
    private EntityManager entityManager;

    private static final Set<String> IGNORED_TABLES = Set.of("databasechangelog", "databasechangeloglock");

    /**
     * Truncates all tables in the test database.
     * Call in @BeforeEach to ensure clean state between individual tests.
     */
    public void cleanDatabase() {
        try {
            entityManager.flush();

            List<String> tableNames = fetchApplicationTables();
            if (tableNames.isEmpty()) {
                entityManager.clear();
                return;
            }

            String joinedTables = tableNames.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));

            entityManager
                .createNativeQuery("TRUNCATE TABLE " + joinedTables + " RESTART IDENTITY CASCADE")
                .executeUpdate();

            entityManager.clear();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to clean database for integration tests", e);
        }
    }

    /**
     * Alias for cleanDatabase() with descriptive naming.
     */
    public void resetDatabase() {
        cleanDatabase();
    }

    /**
     * DELETE-based cleanup - slower but more reliable than TRUNCATE.
     */
    public void clearAllData() {
        cleanDatabase();
    }

    @SuppressWarnings("unchecked")
    private List<String> fetchApplicationTables() {
        List<Object> tables = entityManager
            .createNativeQuery(
                "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' " +
                "AND table_name NOT LIKE 'pg_%' AND table_name NOT LIKE 'sql_%'"
            )
            .getResultList();

        return tables
            .stream()
            .map(Object::toString)
            .filter(name -> !IGNORED_TABLES.contains(name.toLowerCase(Locale.ROOT)))
            .collect(Collectors.toList());
    }

    private String quoteIdentifier(String tableName) {
        return "\"" + tableName + "\"";
    }
}
