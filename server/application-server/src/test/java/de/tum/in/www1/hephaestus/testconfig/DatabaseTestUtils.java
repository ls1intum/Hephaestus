package de.tum.in.www1.hephaestus.testconfig;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    /** Cached truncate statement to avoid schema metadata queries on every test. */
    private static volatile String cachedTruncateStatement = null;

    /** Maximum retries for transient deadlock errors during cleanup. */
    private static final int MAX_RETRIES = 3;

    /** Base delay in milliseconds between retries (doubles with each retry). */
    private static final long RETRY_DELAY_MS = 50;

    /**
     * Truncates all tables in the test database.
     * Call in @BeforeEach to ensure clean state between individual tests.
     * <p>
     * Uses retry logic to handle transient deadlocks that can occur when
     * async event handlers (e.g., ActivityEventService) are still writing
     * when the next test tries to clean the database.
     */
    public void cleanDatabase() {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                entityManager.flush();

                String truncateStatement = getTruncateStatement();
                if (truncateStatement.isEmpty()) {
                    entityManager.clear();
                    return;
                }

                entityManager.createNativeQuery(truncateStatement).executeUpdate();
                entityManager.clear();
                return; // Success
            } catch (Exception e) {
                lastException = e;
                entityManager.clear(); // Clear any failed transaction state

                // Check if this is a transient deadlock error
                if (isDeadlockException(e) && attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * (1L << (attempt - 1))); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted during database cleanup retry", ie);
                    }
                } else {
                    break; // Not a deadlock or max retries reached
                }
            }
        }

        throw new IllegalStateException("Failed to clean database for integration tests", lastException);
    }

    /**
     * Checks if the exception is a transient PostgreSQL deadlock.
     */
    private boolean isDeadlockException(Exception e) {
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("deadlock detected") || message.contains("40P01"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String getTruncateStatement() {
        if (cachedTruncateStatement == null) {
            synchronized (DatabaseTestUtils.class) {
                if (cachedTruncateStatement == null) {
                    List<String> tableNames = fetchApplicationTables();
                    if (tableNames.isEmpty()) {
                        cachedTruncateStatement = "";
                    } else {
                        String joinedTables = tableNames
                            .stream()
                            .map(this::quoteIdentifier)
                            .collect(Collectors.joining(", "));
                        cachedTruncateStatement = "TRUNCATE TABLE " + joinedTables + " RESTART IDENTITY CASCADE";
                    }
                }
            }
        }
        return cachedTruncateStatement;
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
