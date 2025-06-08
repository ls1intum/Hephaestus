package de.tum.in.www1.hephaestus.testconfig;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
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
    
    /**
     * Truncates all tables in the test database.
     * Call in @BeforeEach to ensure clean state between individual tests.
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
     * Alias for cleanDatabase() with descriptive naming.
     */
    public void resetDatabase() {
        cleanDatabase();
    }
    
    /**
     * DELETE-based cleanup - slower but more reliable than TRUNCATE.
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
