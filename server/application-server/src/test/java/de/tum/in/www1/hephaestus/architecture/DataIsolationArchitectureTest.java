package de.tum.in.www1.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static de.tum.in.www1.hephaestus.architecture.ArchitectureTestConstants.*;
import static de.tum.in.www1.hephaestus.architecture.conditions.HephaestusConditions.*;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Data Isolation Architecture Tests - Prevents Cross-Workspace Data Access.
 *
 * <h2>CRITICAL: These tests are the last line of defense against data leakage</h2>
 *
 * <p>These tests enforce patterns that prevent accidental cross-workspace data access:
 * <ul>
 *   <li><b>Entity workspace relationships</b> - Entities should have workspace path</li>
 *   <li><b>Query result mapping</b> - Verify DTOs include workspace context</li>
 *   <li><b>API response boundaries</b> - Ensure responses don't leak cross-workspace data</li>
 * </ul>
 *
 * @see MultiTenancyArchitectureTest for runtime context enforcement
 * @see ArchitectureTestConstants
 */
@DisplayName("Data Isolation Architecture")
class DataIsolationArchitectureTest extends HephaestusArchitectureTest {

    /**
     * Entity types that are workspace-scoped through their relationship chain.
     * Format: EntityName -> path to workspace (for documentation).
     */
    private static final Set<String> WORKSPACE_SCOPED_ENTITIES = Set.of(
        // Direct workspace relationship
        "ActivityEvent", // has Workspace field
        "RepositoryToMonitor", // has Workspace field
        "WorkspaceMembership", // has Workspace field
        "ChatThread", // has Workspace field
        // Through repository -> Workspace.organization (JOIN)
        "PullRequest",
        "Issue",
        "PullRequestReview",
        "PullRequestReviewComment",
        "PullRequestReviewThread",
        "IssueComment",
        "Label",
        "Milestone",
        // Through Workspace.organization (JOIN)
        "Repository",
        "Team",
        "TeamMembership",
        // Bad practice entities through PR
        "BadPracticeDetection",
        "PullRequestBadPractice",
        "BadPracticeFeedback",
        // Through chat thread -> workspace
        "ChatMessage", // through ChatThread.workspace
        "ChatMessagePart", // through ChatMessage.thread.workspace
        "ChatMessageVote", // through ChatMessage (via messageId) -> ChatThread.workspace
        // Through Workspace.organization (ID-based relationship via JOIN)
        "OrganizationMembership" // organizationId -> Workspace.organization
    );

    /**
     * Entities that are legitimately global (not workspace-scoped).
     */
    private static final Set<String> GLOBAL_ENTITIES = Set.of(
        "User", // Users can belong to multiple workspaces
        "Organization", // Synced from GitHub, workspace is set separately
        "Workspace", // Is the tenant root
        "WorkspaceSlugHistory", // Tracks workspace slug changes
        "IssueType", // GitHub issue types are workspace-scoped through issue
        "DeadLetterEvent" // System-wide debugging
    );

    // ========================================================================
    // ENTITY WORKSPACE RELATIONSHIPS
    // ========================================================================

    @Nested
    @DisplayName("Entity Workspace Relationships")
    class EntityWorkspaceRelationshipTests {

        /**
         * JPA entities should have a path to workspace for data isolation.
         *
         * <p>Every entity that contains business data must be traceable to a workspace
         * either directly (workspace field) or through relationships (repository.organization.workspaceId).
         */
        @Test
        @DisplayName("JPA entities have workspace relationship path")
        void entitiesHaveWorkspaceRelationshipPath() {
            ArchCondition<JavaClass> haveWorkspacePath = new ArchCondition<>(
                "have a path to workspace (direct or through relationships)"
            ) {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    String entityName = javaClass.getSimpleName();

                    // Skip known global entities
                    if (GLOBAL_ENTITIES.contains(entityName)) {
                        return;
                    }

                    // Skip if known to be workspace-scoped
                    if (WORKSPACE_SCOPED_ENTITIES.contains(entityName)) {
                        return;
                    }

                    // Check for direct workspace field
                    boolean hasDirectWorkspace = javaClass
                        .getFields()
                        .stream()
                        .anyMatch(
                            f -> f.getRawType().getSimpleName().equals("Workspace") || f.getName().equals("workspaceId")
                        );

                    // Check for workspace-scoped parent relationships
                    Set<String> fieldTypes = javaClass
                        .getFields()
                        .stream()
                        .map(f -> f.getRawType().getSimpleName())
                        .collect(Collectors.toSet());

                    boolean hasWorkspaceScopedParent = fieldTypes
                        .stream()
                        .anyMatch(
                            t ->
                                t.equals("Repository") ||
                                t.equals("Organization") ||
                                t.equals("PullRequest") ||
                                t.equals("Issue") ||
                                t.equals("Team") ||
                                WORKSPACE_SCOPED_ENTITIES.contains(t)
                        );

                    if (!hasDirectWorkspace && !hasWorkspaceScopedParent) {
                        events.add(
                            SimpleConditionEvent.violated(
                                javaClass,
                                String.format(
                                    "ENTITY ISOLATION: %s is a JPA entity with no apparent workspace relationship. " +
                                        "Add to WORKSPACE_SCOPED_ENTITIES with relationship path, " +
                                        "or add to GLOBAL_ENTITIES if intentionally global.",
                                    entityName
                                )
                            )
                        );
                    }
                }
            };

            ArchRule rule = classes()
                .that()
                .areAnnotatedWith(jakarta.persistence.Entity.class)
                .and()
                .resideInAPackage(BASE_PACKAGE + "..")
                .should(haveWorkspacePath)
                .because("All business entities must be traceable to a workspace for data isolation");

            rule.check(classes);
        }

        /**
         * Entities with Workspace field should not be null.
         *
         * <p>If an entity has a direct Workspace relationship, it should
         * use @NotNull or equivalent to prevent orphaned data.
         */
        @Test
        @DisplayName("Direct workspace relationships are not nullable")
        void directWorkspaceRelationshipsNotNullable() {
            ArchRule rule = fields()
                .that()
                .areDeclaredInClassesThat()
                .areAnnotatedWith(jakarta.persistence.Entity.class)
                .should(beNotNullableIfWorkspaceType())
                .because("Workspace relationships should be required to prevent orphaned data");

            rule.check(classes);
        }
    }

    // ========================================================================
    // DTO WORKSPACE CONTEXT
    // ========================================================================

    @Nested
    @DisplayName("DTO Workspace Context")
    class DtoWorkspaceContextTests {

        /**
         * DTOs used in responses should not include cross-workspace references.
         *
         * <p>When mapping entities to DTOs, workspace-scoped data should
         * remain within that workspace. DTOs should not accidentally include
         * data from other workspaces through eager fetching or incorrect joins.
         */
        @Test
        @DisplayName("DTOs do not expose cross-workspace references")
        void dtosDoNotExposeCrossWorkspaceReferences() {
            ArchCondition<JavaClass> notExposeCrossWorkspaceData = new ArchCondition<>(
                "not expose fields that could leak cross-workspace data"
            ) {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    // Check record components or fields
                    Set<String> fieldTypes = javaClass
                        .getFields()
                        .stream()
                        .map(f -> f.getRawType().getSimpleName())
                        .collect(Collectors.toSet());

                    // Dangerous: exposing User directly could leak user's other workspace data
                    // Dangerous: exposing Organization could include data from all workspaces
                    Set<String> dangerousTypes = Set.of("User", "Organization");

                    Set<String> exposedDangerousTypes = fieldTypes
                        .stream()
                        .filter(dangerousTypes::contains)
                        .collect(Collectors.toSet());

                    // Exception: UserDTO, AuthorDTO are safe (workspace-filtered projections)
                    boolean usesSafeProjections = javaClass
                        .getFields()
                        .stream()
                        .map(f -> f.getRawType().getSimpleName())
                        .allMatch(
                            t ->
                                !dangerousTypes.contains(t) ||
                                t.endsWith("DTO") ||
                                t.endsWith("Dto") ||
                                t.endsWith("Data") ||
                                t.endsWith("Info")
                        );

                    if (!exposedDangerousTypes.isEmpty() && !usesSafeProjections) {
                        events.add(
                            SimpleConditionEvent.violated(
                                javaClass,
                                String.format(
                                    "DTO LEAK RISK: %s exposes %s directly. Use projection DTOs " +
                                        "(e.g., UserDTO, AuthorDTO) instead to prevent cross-workspace data leakage.",
                                    javaClass.getSimpleName(),
                                    exposedDangerousTypes
                                )
                            )
                        );
                    }
                }
            };

            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("DTO")
                .or()
                .haveSimpleNameEndingWith("Dto")
                .and()
                .resideInAPackage(BASE_PACKAGE + "..")
                .and()
                .resideOutsideOfPackage("..intelligenceservice..")
                .should(notExposeCrossWorkspaceData)
                .because("DTOs should use projections to prevent cross-workspace data leakage");

            rule.check(classes);
        }
    }

    // ========================================================================
    // REPOSITORY RETURN TYPE SAFETY
    // ========================================================================

    @Nested
    @DisplayName("Repository Return Type Safety")
    class RepositoryReturnTypeSafetyTests {

        /**
         * Repository methods returning entities should be workspace-scoped.
         *
         * <p>Methods that return full entity objects (not projections) should
         * always filter by workspace. Unscoped methods returning full entities
         * are a data isolation risk.
         */
        @Test
        @DisplayName("Entity-returning repository methods are workspace-scoped")
        void entityReturningMethodsAreWorkspaceScoped() {
            ArchCondition<JavaMethod> beWorkspaceScopedIfReturningEntity = new ArchCondition<>(
                "be workspace-scoped if returning entity types"
            ) {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    String methodName = method.getName();
                    String repoName = method.getOwner().getSimpleName();

                    // Skip standard JpaRepository methods (we can't change those)
                    Set<String> standardMethods = Set.of(
                        "findById",
                        "findAll",
                        "findAllById",
                        "save",
                        "saveAll",
                        "delete",
                        "deleteById",
                        "deleteAll",
                        "count",
                        "existsById"
                    );
                    if (standardMethods.contains(methodName)) {
                        return;
                    }

                    // Skip if repo is globally-scoped
                    if (MultiTenancyArchitectureTest.WORKSPACE_AGNOSTIC_REPOSITORIES.contains(repoName)) {
                        return;
                    }

                    String methodKey = repoName + "." + methodName;
                    if (MultiTenancyArchitectureTest.WORKSPACE_AGNOSTIC_METHODS.contains(methodKey)) {
                        return;
                    }

                    // Skip methods or classes annotated with @WorkspaceAgnostic
                    if (method.isAnnotatedWith(WorkspaceAgnostic.class)) {
                        return;
                    }

                    // Skip if the repository class is annotated as workspace-agnostic
                    if (method.getOwner().isAnnotatedWith(WorkspaceAgnostic.class)) {
                        return;
                    }

                    // Check if method returns an entity (not DTO or projection)
                    String returnType = method.getRawReturnType().getSimpleName();
                    boolean returnsEntity = WORKSPACE_SCOPED_ENTITIES.stream().anyMatch(
                        e ->
                            returnType.equals(e) ||
                            returnType.contains("List<" + e) ||
                            returnType.contains("Optional<" + e) ||
                            returnType.contains("Set<" + e)
                    );

                    if (!returnsEntity) {
                        return; // Not returning an entity
                    }

                    // Check for workspace filtering
                    boolean isWorkspaceScoped =
                        methodName.contains("Workspace") ||
                        methodName.contains("workspace") ||
                        methodName.contains("ByWorkspace") ||
                        methodName.contains("ForWorkspace");

                    // Check parameters for workspace
                    boolean hasWorkspaceParam = method
                        .getRawParameterTypes()
                        .stream()
                        .anyMatch(
                            p ->
                                p.getSimpleName().equals("Long") || // workspaceId
                                p.getSimpleName().equals("Workspace")
                        );

                    // Check @Query annotation for workspace filter
                    boolean hasQueryWithWorkspaceFilter = false;
                    if (method.isAnnotatedWith(org.springframework.data.jpa.repository.Query.class)) {
                        org.springframework.data.jpa.repository.Query q = method.getAnnotationOfType(
                            org.springframework.data.jpa.repository.Query.class
                        );
                        hasQueryWithWorkspaceFilter =
                            q.value().contains("workspaceId") ||
                            q.value().contains("workspace.id") ||
                            q.value().contains("JOIN Workspace");
                    }

                    if (!isWorkspaceScoped && !hasWorkspaceParam && !hasQueryWithWorkspaceFilter) {
                        events.add(
                            SimpleConditionEvent.violated(
                                method,
                                String.format(
                                    "ENTITY RETURN RISK: %s.%s returns entity type but appears unscoped. " +
                                        "Add workspace filtering or add to WORKSPACE_AGNOSTIC_METHODS if intentional.",
                                    repoName,
                                    methodName
                                )
                            )
                        );
                    }
                }
            };

            ArchRule rule = methods()
                .that()
                .areDeclaredInClassesThat()
                .haveSimpleNameEndingWith("Repository")
                .and()
                .areDeclaredInClassesThat()
                .areInterfaces()
                .should(beWorkspaceScopedIfReturningEntity)
                .because("Methods returning entities must be workspace-scoped");

            rule.check(classes);
        }
    }

    // ========================================================================
    // CASCADE DELETE WORKSPACE SAFETY
    // ========================================================================

    @Nested
    @DisplayName("Cascade Delete Safety")
    class CascadeDeleteSafetyTests {

        /**
         * Workspace cascade deletes should only affect workspace-scoped entities.
         *
         * <p>When a workspace is deleted, only data belonging to that workspace
         * should be removed. Global entities (User, Organization) should not
         * be cascade-deleted from workspace.
         */
        @Test
        @DisplayName("Workspace does not cascade delete global entities")
        void workspaceDoesNotCascadeDeleteGlobalEntities() {
            ArchCondition<JavaClass> notCascadeDeleteGlobalEntities = new ArchCondition<>(
                "not cascade delete global entities"
            ) {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    if (!javaClass.getSimpleName().equals("Workspace")) {
                        return;
                    }

                    javaClass
                        .getFields()
                        .forEach(field -> {
                            String fieldType = field.getRawType().getSimpleName();

                            // Check if this is a global entity
                            if (!GLOBAL_ENTITIES.contains(fieldType)) {
                                return;
                            }

                            // Check for cascade delete annotations
                            boolean hasCascadeDelete = field
                                .getAnnotations()
                                .stream()
                                .anyMatch(a -> {
                                    String annotationName = a.getRawType().getSimpleName();
                                    if (
                                        !annotationName.equals("OneToMany") &&
                                        !annotationName.equals("OneToOne") &&
                                        !annotationName.equals("ManyToMany")
                                    ) {
                                        return false;
                                    }
                                    try {
                                        Object cascade = a.getExplicitlyDeclaredProperty("cascade");
                                        if (cascade == null) return false;
                                        String cascadeStr = cascade.toString();
                                        return cascadeStr.contains("REMOVE") || cascadeStr.contains("ALL");
                                    } catch (Exception e) {
                                        return false;
                                    }
                                });

                            if (hasCascadeDelete) {
                                events.add(
                                    SimpleConditionEvent.violated(
                                        field,
                                        String.format(
                                            "CASCADE DELETE RISK: Workspace.%s has cascade delete to global entity %s. " +
                                                "This could delete shared data. Remove cascade or use orphanRemoval=false.",
                                            field.getName(),
                                            fieldType
                                        )
                                    )
                                );
                            }
                        });
                }
            };

            ArchRule rule = classes()
                .that()
                .areAnnotatedWith(jakarta.persistence.Entity.class)
                .should(notCascadeDeleteGlobalEntities)
                .because("Workspace deletion should not cascade to global entities");

            rule.check(classes);
        }
    }
}
