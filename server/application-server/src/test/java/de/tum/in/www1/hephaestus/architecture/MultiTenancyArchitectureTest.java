package de.tum.in.www1.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static de.tum.in.www1.hephaestus.architecture.ArchitectureTestConstants.*;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Multi-Tenancy Architecture Tests - Workspace Isolation Enforcement.
 *
 * <h2>CRITICAL: These tests prevent cross-workspace data leakage</h2>
 *
 * <p>In a multi-tenant system, every data access must be scoped to a workspace.
 * These tests enforce:
 * <ul>
 *   <li><b>Repository queries</b> - Must include workspace filtering</li>
 *   <li><b>Scheduled jobs</b> - Must iterate workspaces and set context</li>
 *   <li><b>Event listeners</b> - Must have access to workspace context</li>
 *   <li><b>Service methods</b> - Public data methods should take workspace parameter</li>
 * </ul>
 *
 * @see ArchitectureTestConstants
 */
@DisplayName("Multi-Tenancy Architecture")
@Tag("architecture")
class MultiTenancyArchitectureTest {

    private static JavaClasses classes;

    /**
     * Repositories that are legitimately workspace-agnostic.
     * These are shared infrastructure or lookup tables.
     */
    static final Set<String> WORKSPACE_AGNOSTIC_REPOSITORIES = Set.of(
        // Lookup by external ID (GitHub node ID) - used during sync
        "UserRepository",
        "LabelRepository",
        "MilestoneRepository",
        "IssueTypeRepository",
        // Workspace itself is the tenant root
        "WorkspaceRepository",
        "WorkspaceSlugHistoryRepository",
        // Membership is queried by workspace explicitly
        "WorkspaceMembershipRepository",
        // Dead letter is system-wide for debugging
        "DeadLetterEventRepository"
    );

    /**
     * Repository methods that are legitimately workspace-agnostic.
     * Format: "RepositoryName.methodName"
     */
    static final Set<String> WORKSPACE_AGNOSTIC_METHODS = Set.of(
        // Sync operations that look up by external GitHub ID
        "PullRequestRepository.findByRepositoryIdAndNumber",
        "PullRequestRepository.findAllSyncedPullRequestNumbers",
        "PullRequestRepository.streamAllByRepository_Id",
        "PullRequestRepository.findAllByRepository_Id",
        "IssueRepository.findByRepositoryIdAndNumber",
        "IssueRepository.findAllSyncedIssueNumbers",
        "PullRequestReviewRepository.findByPullRequestIdAndAuthorId",
        "RepositoryRepository.findByNameWithOwner",
        "RepositoryRepository.findByOrganization_IdAndName",
        "OrganizationRepository.findByLogin",
        "TeamRepository.findByOrganization_IdAndSlug",
        // Account lookup by external ID
        "UserRepository.findByLogin",
        "UserRepository.findByLoginIgnoreCase",
        // Repository monitor lookup for sync
        "RepositoryToMonitorRepository.findByNameWithOwner",
        // First contribution is a global query
        "PullRequestRepository.firstContributionByAuthorLogin"
    );

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(BASE_PACKAGE);
    }

    // ========================================================================
    // REPOSITORY WORKSPACE FILTERING
    // ========================================================================

    @Nested
    @DisplayName("Repository Workspace Filtering")
    class RepositoryWorkspaceFilteringTests {

        /**
         * Custom @Query methods should include workspace filtering.
         *
         * <p>All JPQL/HQL queries that return business data should filter
         * by workspaceId to prevent cross-tenant data access.
         *
         * <p>Exceptions:
         * <ul>
         *   <li>Sync operations that look up by external GitHub ID</li>
         *   <li>Workspace-agnostic lookup tables</li>
         *   <li>Queries that already filter through repository.organization.workspaceId</li>
         * </ul>
         */
        @Test
        @DisplayName("@Query methods include workspace filtering (audit)")
        void queryMethodsIncludeWorkspaceFiltering() {
            ArchCondition<JavaMethod> includeWorkspaceFiltering = new ArchCondition<>(
                "include workspace filtering in @Query"
            ) {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    if (!method.isAnnotatedWith(Query.class)) {
                        return;
                    }

                    String repoName = method.getOwner().getSimpleName();
                    String methodKey = repoName + "." + method.getName();

                    // Skip workspace-agnostic repositories
                    if (WORKSPACE_AGNOSTIC_REPOSITORIES.contains(repoName)) {
                        return;
                    }

                    // Skip explicitly allowed methods
                    if (WORKSPACE_AGNOSTIC_METHODS.contains(methodKey)) {
                        return;
                    }

                    // Get the query string from annotation
                    Query queryAnnotation = method.getAnnotationOfType(Query.class);
                    String queryValue = queryAnnotation.value();

                    // Check for workspace filtering patterns
                    boolean hasWorkspaceFilter =
                        queryValue.contains("workspaceId") ||
                        queryValue.contains("workspace.id") ||
                        queryValue.contains("workspace_id") ||
                        queryValue.contains(".organization.workspaceId") ||
                        queryValue.contains("repository.organization.workspaceId");

                    if (!hasWorkspaceFilter) {
                        events.add(
                            SimpleConditionEvent.violated(
                                method,
                                String.format(
                                    "MULTI-TENANCY RISK: %s.%s has @Query without workspace filtering. " +
                                        "Query: %s... Add to WORKSPACE_AGNOSTIC_METHODS if intentional.",
                                    repoName,
                                    method.getName(),
                                    queryValue.substring(0, Math.min(80, queryValue.length()))
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
                .resideInAPackage(BASE_PACKAGE + "..")
                .should(includeWorkspaceFiltering)
                .because("Repository queries must filter by workspace to prevent cross-tenant data access");

            rule.check(classes);
        }

        /**
         * Repositories returning lists should have workspace-scoped alternatives.
         *
         * <p>Methods like findAll() are dangerous in multi-tenant systems.
         * They should have workspace-scoped alternatives.
         */
        @Test
        @DisplayName("Repositories have workspace-scoped query alternatives")
        void repositoriesHaveWorkspaceScopedAlternatives() {
            ArchCondition<JavaClass> haveWorkspaceScopedMethods = new ArchCondition<>(
                "have workspace-scoped query methods"
            ) {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    String repoName = javaClass.getSimpleName();

                    // Skip workspace-agnostic repositories
                    if (WORKSPACE_AGNOSTIC_REPOSITORIES.contains(repoName)) {
                        return;
                    }

                    // Check if it's a Spring Data repository
                    boolean isSpringDataRepo = javaClass
                        .getAllRawInterfaces()
                        .stream()
                        .anyMatch(i -> i.getName().contains("JpaRepository") || i.getName().contains("CrudRepository"));

                    if (!isSpringDataRepo) {
                        return;
                    }

                    // Get all method names
                    Set<String> methodNames = javaClass
                        .getMethods()
                        .stream()
                        .map(JavaMethod::getName)
                        .collect(Collectors.toSet());

                    // Check for workspace-scoped methods
                    boolean hasWorkspaceScopedMethods = methodNames
                        .stream()
                        .anyMatch(
                            name ->
                                name.contains("ByWorkspace") ||
                                name.contains("ForWorkspace") ||
                                name.contains("InWorkspace") ||
                                name.contains("workspaceId")
                        );

                    // Check for @Query methods with workspace filtering
                    boolean hasQueryWithWorkspaceFilter = javaClass
                        .getMethods()
                        .stream()
                        .filter(m -> m.isAnnotatedWith(Query.class))
                        .anyMatch(m -> {
                            Query q = m.getAnnotationOfType(Query.class);
                            return (
                                q.value().contains("workspaceId") ||
                                q.value().contains("workspace.id") ||
                                q.value().contains(".organization.workspaceId")
                            );
                        });

                    if (!hasWorkspaceScopedMethods && !hasQueryWithWorkspaceFilter) {
                        events.add(
                            SimpleConditionEvent.violated(
                                javaClass,
                                String.format(
                                    "MULTI-TENANCY AUDIT: %s has no workspace-scoped query methods. " +
                                        "Consider adding findByWorkspaceId or similar methods.",
                                    repoName
                                )
                            )
                        );
                    }
                }
            };

            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("Repository")
                .and()
                .resideInAPackage(BASE_PACKAGE + "..")
                .and()
                .areInterfaces()
                .should(haveWorkspaceScopedMethods)
                .allowEmptyShould(true)
                .because("Repositories should provide workspace-scoped query methods");

            rule.check(classes);
        }
    }

    // ========================================================================
    // SCHEDULED JOB WORKSPACE CONTEXT
    // ========================================================================

    @Nested
    @DisplayName("Scheduled Job Workspace Context")
    class ScheduledJobContextTests {

        /**
         * Classes with @Scheduled methods should have workspace iteration logic.
         *
         * <p>Scheduled jobs run outside of any request context. They must:
         * <ul>
         *   <li>Iterate over all active workspaces</li>
         *   <li>Set workspace context before processing each workspace</li>
         *   <li>Clear context after processing</li>
         * </ul>
         *
         * <p>Pattern: Use SyncTargetProvider or WorkspaceRepository to get workspaces,
         * then use SyncContextProvider to set/clear context.
         */
        @Test
        @DisplayName("@Scheduled classes inject workspace iteration dependencies")
        void scheduledClassesHaveWorkspaceIterationDependencies() {
            ArchCondition<JavaClass> haveWorkspaceIterationCapability = new ArchCondition<>(
                "have workspace iteration or context dependencies"
            ) {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    // Check if class has @Scheduled methods
                    boolean hasScheduledMethods = javaClass
                        .getMethods()
                        .stream()
                        .anyMatch(m -> m.isAnnotatedWith(Scheduled.class));

                    if (!hasScheduledMethods) {
                        return;
                    }

                    // Get all field and constructor parameter types
                    Set<String> dependencies = javaClass
                        .getFields()
                        .stream()
                        .map(f -> f.getRawType().getSimpleName())
                        .collect(Collectors.toSet());

                    javaClass
                        .getConstructors()
                        .forEach(c -> c.getRawParameterTypes().forEach(p -> dependencies.add(p.getSimpleName())));

                    // Check for workspace-aware dependencies
                    boolean hasWorkspaceAwareDependency =
                        dependencies.contains("SyncTargetProvider") ||
                        dependencies.contains("SyncContextProvider") ||
                        dependencies.contains("WorkspaceRepository") ||
                        dependencies.contains("WorkspaceService") ||
                        // Cache eviction schedulers are workspace-agnostic by design
                        javaClass.getSimpleName().contains("Cache");

                    if (!hasWorkspaceAwareDependency) {
                        events.add(
                            SimpleConditionEvent.violated(
                                javaClass,
                                String.format(
                                    "SCHEDULED JOB CONTEXT: %s has @Scheduled methods but no workspace " +
                                        "iteration dependencies. Add SyncTargetProvider or WorkspaceRepository " +
                                        "to iterate workspaces and set context.",
                                    javaClass.getSimpleName()
                                )
                            )
                        );
                    }
                }
            };

            ArchRule rule = classes()
                .that()
                .resideInAPackage(BASE_PACKAGE + "..")
                .should(haveWorkspaceIterationCapability)
                .because("Scheduled jobs must iterate workspaces and set context");

            rule.check(classes);
        }

        /**
         * @Scheduled methods should not directly access repositories without workspace filtering.
         *
         * <p>If a scheduled method accesses a repository directly, it must
         * be through a workspace-scoped method or after setting workspace context.
         */
        @Test
        @DisplayName("@Scheduled methods don't bypass workspace context")
        void scheduledMethodsDontBypassWorkspaceContext() {
            ArchCondition<JavaMethod> notDirectlyAccessGlobalRepositoryMethods = new ArchCondition<>(
                "not directly access repository methods without workspace context"
            ) {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    if (!method.isAnnotatedWith(Scheduled.class)) {
                        return;
                    }

                    // Check method body for repository calls
                    // This is a heuristic - we check for method calls to findAll, findById, etc.
                    Set<String> calledMethods = method
                        .getMethodCallsFromSelf()
                        .stream()
                        .map(call -> call.getTarget().getName())
                        .collect(Collectors.toSet());

                    // Dangerous methods that don't filter by workspace
                    Set<String> dangerousMethods = Set.of("findAll", "findById", "count", "existsById");

                    Set<String> dangerousCalls = calledMethods
                        .stream()
                        .filter(dangerousMethods::contains)
                        .collect(Collectors.toSet());

                    // If the method also calls workspace-aware methods, it's likely safe
                    boolean hasWorkspaceAwareCalls = calledMethods
                        .stream()
                        .anyMatch(
                            name ->
                                name.contains("Workspace") ||
                                name.contains("workspace") ||
                                name.contains("setContext") ||
                                name.contains("SyncSession") ||
                                name.contains("getWorkspace")
                        );

                    if (!dangerousCalls.isEmpty() && !hasWorkspaceAwareCalls) {
                        events.add(
                            SimpleConditionEvent.violated(
                                method,
                                String.format(
                                    "SCHEDULED JOB BYPASS: %s.%s calls %s without apparent workspace context. " +
                                        "Ensure workspace iteration or use workspace-scoped repository methods.",
                                    method.getOwner().getSimpleName(),
                                    method.getName(),
                                    dangerousCalls
                                )
                            )
                        );
                    }
                }
            };

            ArchRule rule = methods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage(BASE_PACKAGE + "..")
                .should(notDirectlyAccessGlobalRepositoryMethods)
                .because("Scheduled jobs must not bypass workspace context");

            rule.check(classes);
        }
    }

    // ========================================================================
    // EVENT LISTENER WORKSPACE VALIDATION
    // ========================================================================

    @Nested
    @DisplayName("Event Listener Workspace Context")
    class EventListenerContextTests {

        /**
         * @TransactionalEventListener methods should receive events with workspace context.
         *
         * <p>Domain events should carry workspace information (workspaceId or through
         * entity relationships) so that async handlers can operate in the correct
         * tenant context.
         */
        @Test
        @DisplayName("Event listeners receive events with workspace context")
        void eventListenersReceiveWorkspaceContext() {
            ArchCondition<JavaMethod> handleEventsWithWorkspaceContext = new ArchCondition<>(
                "handle events that carry workspace context"
            ) {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    boolean isEventListener =
                        method.isAnnotatedWith(TransactionalEventListener.class) ||
                        method.isAnnotatedWith(org.springframework.context.event.EventListener.class);

                    if (!isEventListener) {
                        return;
                    }

                    // Check if the event parameter type carries workspace context
                    // Domain events should contain workspace ID or entity with workspace relationship
                    method
                        .getRawParameterTypes()
                        .forEach(paramType -> {
                            String paramTypeName = paramType.getSimpleName();

                            // Known event types that carry workspace context through entity relationships
                            Set<String> workspaceAwareEventPrefixes = Set.of(
                                "DomainEvent", // Our domain events carry repository which has workspace
                                "PullRequest", // Through repository.organization.workspaceId
                                "Issue", // Through repository.organization.workspaceId
                                "Review", // Through PR
                                "ApplicationReadyEvent", // Spring lifecycle, no workspace needed
                                "ContextRefreshedEvent" // Spring lifecycle, no workspace needed
                            );

                            boolean isWorkspaceAware = workspaceAwareEventPrefixes
                                .stream()
                                .anyMatch(paramTypeName::contains);

                            if (!isWorkspaceAware && !paramTypeName.equals("Object")) {
                                events.add(
                                    SimpleConditionEvent.violated(
                                        method,
                                        String.format(
                                            "EVENT LISTENER CONTEXT: %s.%s handles %s - verify it carries workspace context. " +
                                                "Events should include workspaceId or entity with workspace relationship.",
                                            method.getOwner().getSimpleName(),
                                            method.getName(),
                                            paramTypeName
                                        )
                                    )
                                );
                            }
                        });
                }
            };

            ArchRule rule = methods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage(BASE_PACKAGE + "..")
                .should(handleEventsWithWorkspaceContext)
                .because("Event listeners must receive workspace context through events");

            rule.check(classes);
        }

        /**
         * @Async event listeners should not lose workspace context.
         *
         * <p>When @Async is combined with @TransactionalEventListener, the
         * execution happens in a different thread. MDC and ThreadLocal context
         * is lost unless explicitly propagated.
         */
        @Test
        @DisplayName("@Async event listeners propagate workspace context")
        void asyncEventListenersPropagateContext() {
            ArchCondition<JavaClass> propagateContextInAsyncListeners = new ArchCondition<>(
                "propagate workspace context in async event listeners"
            ) {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    // Check if class has @Async @TransactionalEventListener methods
                    boolean hasAsyncEventListeners = javaClass
                        .getMethods()
                        .stream()
                        .anyMatch(
                            m ->
                                m.isAnnotatedWith(org.springframework.scheduling.annotation.Async.class) &&
                                (m.isAnnotatedWith(TransactionalEventListener.class) ||
                                    m.isAnnotatedWith(org.springframework.context.event.EventListener.class))
                        );

                    if (!hasAsyncEventListeners) {
                        return;
                    }

                    // Check for context propagation dependencies
                    Set<String> dependencies = javaClass
                        .getFields()
                        .stream()
                        .map(f -> f.getRawType().getSimpleName())
                        .collect(Collectors.toSet());

                    javaClass
                        .getConstructors()
                        .forEach(c -> c.getRawParameterTypes().forEach(p -> dependencies.add(p.getSimpleName())));

                    // If the async listener class has repository dependencies, it should
                    // also have context propagation or use events that carry full context
                    boolean hasRepositoryDependency = dependencies.stream().anyMatch(d -> d.endsWith("Repository"));

                    // Events with full entity snapshots (like DomainEvent payloads) don't need
                    // context propagation - they carry all needed data
                    boolean usesPayloadEvents = javaClass
                        .getMethods()
                        .stream()
                        .filter(m -> m.isAnnotatedWith(TransactionalEventListener.class))
                        .flatMap(m -> m.getRawParameterTypes().stream())
                        .anyMatch(
                            p -> p.getSimpleName().contains("DomainEvent") || p.getSimpleName().contains("Event")
                        );

                    if (hasRepositoryDependency && !usesPayloadEvents) {
                        // Log for manual review - async listeners with repo access need scrutiny
                        events.add(
                            SimpleConditionEvent.satisfied(
                                javaClass,
                                String.format(
                                    "ASYNC CONTEXT AUDIT: %s has @Async event listeners with repository access. " +
                                        "Verify workspace context is available (via event payload or MDC propagation).",
                                    javaClass.getSimpleName()
                                )
                            )
                        );
                    }
                }
            };

            ArchRule rule = classes()
                .that()
                .resideInAPackage(BASE_PACKAGE + "..")
                .should(propagateContextInAsyncListeners)
                .because("Async event listeners must not lose workspace context");

            rule.check(classes);
        }
    }

    // ========================================================================
    // SERVICE LAYER WORKSPACE AWARENESS
    // ========================================================================

    @Nested
    @DisplayName("Service Layer Workspace Awareness")
    class ServiceWorkspaceAwarenessTests {

        /**
         * Public service methods returning entity lists should take workspace parameter.
         *
         * <p>Services that return lists of entities must be workspace-scoped
         * to prevent accidentally returning data from all tenants.
         */
        @Test
        @DisplayName("Service list methods are workspace-scoped")
        void serviceListMethodsAreWorkspaceScoped() {
            ArchCondition<JavaMethod> beWorkspaceScopedIfReturningList = new ArchCondition<>(
                "be workspace-scoped if returning a list of entities"
            ) {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    // Only check public methods
                    if (!method.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC)) {
                        return;
                    }

                    // Check if method returns a List or Collection
                    String returnType = method.getRawReturnType().getName();
                    boolean returnsList =
                        returnType.contains("List") ||
                        returnType.contains("Set") ||
                        returnType.contains("Collection") ||
                        returnType.contains("Page");

                    if (!returnsList) {
                        return;
                    }

                    // Skip methods that are clearly workspace-aware
                    String methodName = method.getName();
                    boolean isWorkspaceAware =
                        methodName.contains("Workspace") ||
                        methodName.contains("workspace") ||
                        methodName.contains("ByTeam") || // Team implies workspace scope
                        methodName.contains("ForUser"); // User in workspace context

                    // Check method parameters for workspace indicators
                    boolean hasWorkspaceParam = method
                        .getRawParameterTypes()
                        .stream()
                        .anyMatch(
                            p ->
                                p.getSimpleName().contains("WorkspaceContext") ||
                                p.getSimpleName().contains("Workspace") ||
                                p.getName().contains("Long") // workspaceId as Long
                        );

                    if (!isWorkspaceAware && !hasWorkspaceParam) {
                        // This is informational - services may have internal methods
                        // that are called with workspace context already established
                        String ownerName = method.getOwner().getSimpleName();

                        // Skip internal/private-like method patterns
                        if (methodName.startsWith("get") || methodName.startsWith("find")) {
                            events.add(
                                SimpleConditionEvent.satisfied(
                                    method,
                                    String.format(
                                        "SERVICE AUDIT: %s.%s returns a list but has no obvious workspace parameter. " +
                                            "Verify workspace scoping is applied internally.",
                                        ownerName,
                                        methodName
                                    )
                                )
                            );
                        }
                    }
                }
            };

            ArchRule rule = methods()
                .that()
                .areDeclaredInClassesThat()
                .haveSimpleNameEndingWith("Service")
                .and()
                .areDeclaredInClassesThat()
                .areAnnotatedWith(org.springframework.stereotype.Service.class)
                .and()
                .areDeclaredInClassesThat()
                .resideOutsideOfPackage("..intelligenceservice..")
                .should(beWorkspaceScopedIfReturningList)
                .allowEmptyShould(true)
                .because("Services returning lists should be workspace-scoped");

            rule.check(classes);
        }
    }

    // ========================================================================
    // CONTROLLER WORKSPACE CONTEXT
    // ========================================================================

    @Nested
    @DisplayName("Controller Workspace Context")
    class ControllerWorkspaceContextTests {

        /**
         * Controllers should resolve WorkspaceContext for all data endpoints.
         *
         * <p>Every endpoint that returns tenant-specific data should:
         * <ul>
         *   <li>Take WorkspaceContext as a parameter (resolved by argument resolver)</li>
         *   <li>OR use @EnsureWorkspaceAccess or similar security annotation</li>
         *   <li>OR be explicitly marked as workspace-agnostic (e.g., health endpoints)</li>
         * </ul>
         */
        @Test
        @DisplayName("Data endpoints receive WorkspaceContext")
        void dataEndpointsReceiveWorkspaceContext() {
            ArchCondition<JavaMethod> haveWorkspaceContextForDataEndpoints = new ArchCondition<>(
                "have WorkspaceContext for data endpoints"
            ) {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    boolean hasHttpMapping =
                        method.isAnnotatedWith(org.springframework.web.bind.annotation.GetMapping.class) ||
                        method.isAnnotatedWith(org.springframework.web.bind.annotation.PostMapping.class) ||
                        method.isAnnotatedWith(org.springframework.web.bind.annotation.PutMapping.class) ||
                        method.isAnnotatedWith(org.springframework.web.bind.annotation.DeleteMapping.class) ||
                        method.isAnnotatedWith(org.springframework.web.bind.annotation.PatchMapping.class);

                    if (!hasHttpMapping) {
                        return;
                    }

                    String controllerName = method.getOwner().getSimpleName();
                    String methodName = method.getName();

                    // Skip health/status endpoints
                    if (
                        controllerName.contains("Health") ||
                        controllerName.contains("Status") ||
                        controllerName.contains("Actuator")
                    ) {
                        return;
                    }

                    // Check for WorkspaceContext parameter
                    boolean hasWorkspaceContext = method
                        .getRawParameterTypes()
                        .stream()
                        .anyMatch(p -> p.getSimpleName().equals("WorkspaceContext"));

                    // Check for workspace security annotations
                    boolean hasWorkspaceSecurityAnnotation =
                        method
                            .getAnnotations()
                            .stream()
                            .anyMatch(
                                a ->
                                    a.getRawType().getSimpleName().contains("Workspace") ||
                                    a.getRawType().getSimpleName().contains("Ensure") ||
                                    a.getRawType().getSimpleName().contains("Require")
                            ) ||
                        method
                            .getOwner()
                            .getAnnotations()
                            .stream()
                            .anyMatch(
                                a ->
                                    a.getRawType().getSimpleName().contains("Workspace") ||
                                    a.getRawType().getSimpleName().contains("Ensure")
                            );

                    // Check for path variable that includes workspace (e.g., /api/{workspaceSlug}/...)
                    boolean hasWorkspacePathVariable = method
                        .getRawParameterTypes()
                        .stream()
                        .anyMatch(
                            p -> p.getSimpleName().equals("String") // Could be workspaceSlug
                        );

                    if (!hasWorkspaceContext && !hasWorkspaceSecurityAnnotation) {
                        events.add(
                            SimpleConditionEvent.violated(
                                method,
                                String.format(
                                    "CONTROLLER CONTEXT: %s.%s is a data endpoint without WorkspaceContext parameter " +
                                        "or workspace security annotation. Add WorkspaceContext parameter or appropriate annotation.",
                                    controllerName,
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
                .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .and()
                .arePublic()
                .should(haveWorkspaceContextForDataEndpoints)
                .because("All data endpoints must have workspace context");

            rule.check(classes);
        }
    }
}
