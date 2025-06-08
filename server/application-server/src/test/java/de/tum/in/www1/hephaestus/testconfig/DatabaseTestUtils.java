package de.tum.in.www1.hephaestus.testconfig;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestContext;

/**
 * Utility class for managing database state in integration tests.
 * 
 * <p>This class provides methods to clean up database state between tests
 * while reusing the same PostgreSQL container. This ensures test isolation
 * while maintaining performance benefits from container reuse.
 * 
 * @author Felix T.J. Dietrich
 */
@Component
@Transactional
public class DatabaseTestUtils {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    /**
     * Truncates all tables in the test database.
     * 
     * <p>This method provides a fast way to clean up all data between tests
     * without recreating the entire database schema.
     * 
     * <p>Usage: Call this method in a @BeforeEach method to ensure clean state
     * between individual tests within a test class.
     */
    public void cleanDatabase() {
        try {
            // Get all table names from the current schema, excluding system tables
            var tableNames = entityManager.createNativeQuery(
                "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' " +
                "AND table_name NOT LIKE 'pg_%' AND table_name NOT LIKE 'sql_%'"
            ).getResultList();
            
            if (tableNames.isEmpty()) {
                return; // No tables to clean
            }
            
            // Disable foreign key checks temporarily
            entityManager.createNativeQuery("SET session_replication_role = replica").executeUpdate();
            
            try {
                // Truncate each table with proper identifier quoting
                for (Object tableName : tableNames) {
                    String quotedTableName = "\"" + tableName.toString() + "\"";
                    entityManager.createNativeQuery("TRUNCATE TABLE " + quotedTableName + " CASCADE")
                        .executeUpdate();
                }
            } finally {
                // Re-enable foreign key checks
                entityManager.createNativeQuery("SET session_replication_role = origin").executeUpdate();
            }
            
            entityManager.flush();
            entityManager.clear();
            
        } catch (Exception e) {
            // Log the error but don't fail the test - the schema recreation handles cleanup
            System.err.println("Warning: Database cleanup failed, relying on schema recreation: " + e.getMessage());
        }
    }
    
    /**
     * Resets the database to a clean state by truncating all tables.
     * This is an alias for cleanDatabase() to provide more descriptive naming.
     */
    public void resetDatabase() {
        cleanDatabase();
    }
    
    /**
     * Alternative cleanup method that uses DELETE statements instead of TRUNCATE.
     * This is safer but slower than truncate, useful as a fallback.
     */
    public void clearAllData() {
        try {
            // Get all table names from the current schema
            var tableNames = entityManager.createNativeQuery(
                "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' " +
                "AND table_name NOT LIKE 'pg_%' AND table_name NOT LIKE 'sql_%'"
            ).getResultList();
            
            // Delete from each table (slower but more reliable than TRUNCATE)
            for (Object tableName : tableNames) {
                String quotedTableName = "\"" + tableName.toString() + "\"";
                entityManager.createNativeQuery("DELETE FROM " + quotedTableName).executeUpdate();
            }
            
            entityManager.flush();
            entityManager.clear();
            
        } catch (Exception e) {
            System.err.println("Warning: Alternative database cleanup failed: " + e.getMessage());
        }
    }
}
