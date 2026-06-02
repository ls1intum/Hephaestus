package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static de.tum.cit.aet.hephaestus.architecture.ArchitectureTestConstants.*;
import static de.tum.cit.aet.hephaestus.architecture.conditions.HephaestusConditions.*;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Code Quality Tests - Detect anti-patterns and code smells.
 *
 * <p>These tests enforce quality standards:
 * <ul>
 *   <li>God class detection (SRP violations)</li>
 *   <li>Constructor parameter limits</li>
 *   <li>Interface Segregation Principle</li>
 *   <li>Dependency Inversion patterns</li>
 * </ul>
 *
 * <p>All thresholds are defined in {@link ArchitectureTestConstants}.
 * <p>NOTE: Consolidated from SolidPrinciplesTest.java - unique ISP/DIP tests merged here.
 *
 * @see ArchitectureTestConstants
 */
class CodeQualityTest extends HephaestusArchitectureTest {

    // GOD CLASS DETECTION

    @Nested
    class GodClassTests {

        /**
         * Services should not have excessive constructor parameters.
         *
         * <p>More than 12 dependencies indicates a God class that needs splitting.
         *
         * <p><strong>Exceptions:</strong> Orchestrator services that coordinate many sub-services
         * (e.g., GithubDataSyncService) may legitimately have more dependencies.
         * These should be explicitly named here with justification.
         */
        @Test
        void servicesHaveLimitedConstructorParams() {
            // Orchestrator services that coordinate many sub-services are allowed more dependencies
            Set<String> orchestratorExceptions = Set.of(
                "GithubDataSyncService", // Coordinates 15 entity-specific sync services
                "GitHubHistoricalBackfillService", // Coordinates multiple sync services for historical data backfill
                "GitHubPullRequestSyncService", // Coordinates review, review comment, and project item sub-sync services
                "WorkspaceProvisioningService", // Orchestrates provisioning across GitHub and GitLab providers
                "MentorChatService", // Coordinates persistence, SSE, runner, lock, metrics, executor, llm config, context build
                // Full provider-lifecycle orchestrators with broad ConnectionService usage —
                // splitting them further scatters related logic without reducing coupling.
                "GitLabWorkspaceInitializationService",
                "WorkspaceRepositoryMonitorService"
            );

            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("Service")
                .and()
                .areAnnotatedWith(org.springframework.stereotype.Service.class)
                .should(haveAtMostConstructorParameters(MAX_SERVICE_DEPENDENCIES, orchestratorExceptions))
                .because("God classes with many dependencies violate SRP and are hard to test");

            rule.check(classes);
        }

        /**
         * Controllers should be thin with limited dependencies.
         */
        @Test
        @DisplayName("Controllers have max 5 dependencies")
        void controllersAreThin() {
            ArchRule rule = classes()
                .that()
                .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .should(haveAtMostConstructorParameters(MAX_CONTROLLER_DEPENDENCIES))
                .because("Controllers should delegate to services, not orchestrate many dependencies");

            rule.check(classes);
        }

        /**
         * Services should not have excessive business methods.
         *
         * <p>Classes with too many business methods violate SRP and should be split.
         * Business methods exclude getters, setters, equals, hashCode, toString, and constructors.
         */
        @Test
        void servicesHaveLimitedBusinessMethods() {
            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("Service")
                .and()
                .areAnnotatedWith(org.springframework.stereotype.Service.class)
                .should(haveAtMostBusinessMethods(MAX_SERVICE_METHODS))
                .because("Services with many methods violate SRP and should be split into focused services");

            rule.check(classes);
        }
    }

    // METHOD COMPLEXITY LIMITS

    @Nested
    class MethodComplexityTests {

        /** Maximum parameters per method - indicates complex method. */
        private static final int MAX_METHOD_PARAMETERS = 6;

        /**
         * Methods should not have too many parameters.
         *
         * <p>Too many parameters indicates complex methods that are hard
         * to test and maintain. Consider using parameter objects.
         *
         * <p><strong>Exceptions:</strong>
         * <ul>
         *   <li>@Recover methods (Spring Retry requires matching signatures)</li>
         *   <li>Static factory methods (e.g., `simple()`, `of()`, `from()`)</li>
         *   <li>Overloaded internal methods with a command-object based alternative</li>
         * </ul>
         */
        @Test
        void methodsHaveLimitedParameters() {
            // Methods that have command-object overloads but need many params for internal processing
            Set<String> allowedOverloads = Set.of(
                "ActivityEventService.record", // Has RecordActivityCommand overload for cleaner API
                "ActivityRecorder.record", // SPI mirror of ActivityEventService.record — same shape by design
                // @Bean factory wiring Spring dependencies — not business logic complexity
                "DockerSandboxConfiguration.dockerSandboxAdapter",
                "DockerSandboxConfiguration.dockerInteractiveSandboxAdapter"
            );

            // Repository native SQL methods require individual @Param annotations and cannot use
            // parameter objects. These are atomic upsert operations that map directly to DB columns.
            Set<String> nativeSqlRepositoryMethods = Set.of(
                "ActivityEventRepository.insertIfAbsent",
                "IssueRepository.upsertCore",
                "PullRequestRepository.upsertCore",
                "MilestoneRepository.insertIfAbsent",
                "LabelRepository.insertIfAbsent",
                "ProjectRepository.upsertCore",
                "ProjectFieldRepository.upsertCore",
                "ProjectItemRepository.upsertCore",
                "ProjectFieldValueRepository.upsertCore",
                "ProjectStatusUpdateRepository.upsertCore",
                "UserRepository.upsertUser",
                "OrganizationRepository.upsert",
                "CommitRepository.upsertCommit",
                "CommitRepository.updateEnrichmentMetadata",
                "DiscussionCategoryRepository.upsertCategory",
                "DiscussionRepository.upsertCore",
                "PracticeFindingRepository.insertIfAbsent"
            );

            ArchCondition<JavaClass> haveMethodsWithLimitedParams = new ArchCondition<>(
                "have methods with at most " + MAX_METHOD_PARAMETERS + " parameters"
            ) {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    javaClass
                        .getMethods()
                        .stream()
                        .filter(m -> m.getOwner().equals(javaClass)) // Only declared methods
                        .filter(m -> !m.getName().startsWith("$")) // Exclude synthetic
                        .filter(m -> !m.getName().equals("<init>")) // Exclude constructors
                        .filter(m -> !m.getName().startsWith("lambda$")) // Exclude lambdas
                        // Exclude @Recover methods (Spring Retry requires matching signatures)
                        .filter(m -> !m.isAnnotatedWith("org.springframework.retry.annotation.Recover"))
                        // Exclude static factory methods (common pattern for parameter objects)
                        .filter(m ->
                            !(m.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.STATIC) &&
                                (m.getName().equals("simple") ||
                                    m.getName().equals("of") ||
                                    m.getName().equals("from")))
                        )
                        // Exclude allowed overloads with command-object alternatives
                        .filter(m -> !allowedOverloads.contains(javaClass.getSimpleName() + "." + m.getName()))
                        // Exclude native SQL repository methods (require @Param per column, no param objects)
                        .filter(m ->
                            !nativeSqlRepositoryMethods.contains(javaClass.getSimpleName() + "." + m.getName())
                        )
                        .filter(m -> m.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC))
                        .forEach(method -> {
                            int paramCount = method.getRawParameterTypes().size();
                            if (paramCount > MAX_METHOD_PARAMETERS) {
                                events.add(
                                    SimpleConditionEvent.violated(
                                        javaClass,
                                        String.format(
                                            "COMPLEXITY: %s.%s has %d parameters (max %d) - consider parameter object",
                                            javaClass.getSimpleName(),
                                            method.getName(),
                                            paramCount,
                                            MAX_METHOD_PARAMETERS
                                        )
                                    )
                                );
                            }
                        });
                }
            };

            ArchRule rule = classes()
                .that()
                .resideInAPackage(BASE_PACKAGE + "..")
                .and()
                .resideOutsideOfPackage(GENERATED_GRAPHQL_PACKAGE)
                .and()
                .areNotAnonymousClasses()
                .and()
                .areNotMemberClasses()
                .should(haveMethodsWithLimitedParams)
                .because("Too many parameters indicates complex methods needing refactoring");

            rule.check(classes);
        }

        /**
         * Public methods in services should not have excessive nesting indicators.
         *
         * <p>Methods with many boolean parameters often indicate high cyclomatic complexity.
         * This is a proxy check since ArchUnit cannot directly measure cyclomatic complexity.
         */
        @Test
        void serviceMethodsAvoidExcessiveBooleanParams() {
            ArchCondition<JavaClass> avoidManyBooleans = new ArchCondition<>(
                "avoid methods with more than 2 boolean parameters"
            ) {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    javaClass
                        .getMethods()
                        .stream()
                        .filter(m -> m.getOwner().equals(javaClass))
                        .filter(m -> !m.getName().startsWith("$"))
                        .forEach(method -> {
                            long booleanCount = method
                                .getRawParameterTypes()
                                .stream()
                                .filter(p -> p.getName().equals("boolean") || p.getName().equals("java.lang.Boolean"))
                                .count();
                            if (booleanCount > 2) {
                                events.add(
                                    SimpleConditionEvent.violated(
                                        javaClass,
                                        String.format(
                                            "COMPLEXITY: %s.%s has %d boolean params - consider using enum or builder",
                                            javaClass.getSimpleName(),
                                            method.getName(),
                                            booleanCount
                                        )
                                    )
                                );
                            }
                        });
                }
            };

            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("Service")
                .should(avoidManyBooleans)
                .because("Multiple boolean parameters indicate complexity and poor API design");

            rule.check(classes);
        }
    }

    // SECURITY PATTERNS

    @Nested
    @DisplayName("Security Patterns")
    class SecurityPatternTests {

        /**
         * Services handling tokens should be in appropriate security-related packages.
         *
         * <p>Token services are sensitive and should be in one of:
         * <ul>
         *   <li>Security packages (auth, security) - for authentication tokens</li>
         *   <li>App packages - for session tokens</li>
         *   <li>Common packages - for shared token utilities</li>
         *   <li>GitHub packages - for GitHub-specific token handling</li>
         * </ul>
         */
        @Test
        void tokenServicesInSecurityPackages() {
            ArchCondition<JavaClass> beInTokenAppropriatePackage = new ArchCondition<>(
                "be in security, auth, app, common, or github package"
            ) {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    String packageName = javaClass.getPackageName();
                    boolean isInAppropriatePackage =
                        packageName.contains(".app") ||
                        packageName.contains(".auth") ||
                        packageName.contains(".security") ||
                        packageName.contains(".common") ||
                        packageName.contains(".github");

                    if (!isInAppropriatePackage) {
                        events.add(
                            SimpleConditionEvent.violated(
                                javaClass,
                                String.format(
                                    "%s handles tokens but is not in app/auth/security/common/github package",
                                    javaClass.getSimpleName()
                                )
                            )
                        );
                    }
                }
            };

            ArchRule rule = classes()
                .that()
                .haveSimpleNameContaining("Token")
                .and()
                .haveSimpleNameEndingWith("Service")
                .should(beInTokenAppropriatePackage)
                .because("Token handling should be centralized in security-related packages");

            rule.check(classes);
        }
    }

    // INTERFACE SEGREGATION PRINCIPLE (merged from SolidPrinciplesTest)

    @Nested
    class InterfaceSegregationTests {

        /**
         * Interfaces should have limited methods.
         *
         * <p>Fat interfaces force implementations to provide methods they
         * don't need. Prefer small, focused interfaces.
         */
        @Test
        void interfacesHaveLimitedMethods() {
            ArchCondition<JavaClass> haveLimitedMethods = new ArchCondition<>(
                "have at most " + MAX_INTERFACE_METHODS + " methods"
            ) {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    // Skip Spring Data repositories - they have many default methods
                    if (javaClass.isAssignableTo(org.springframework.data.repository.Repository.class)) {
                        return;
                    }

                    int methodCount = (int) javaClass
                        .getMethods()
                        .stream()
                        .filter(m -> !m.getModifiers().contains(JavaModifier.STATIC))
                        .filter(m -> m.getModifiers().contains(JavaModifier.ABSTRACT))
                        .count();

                    if (methodCount > MAX_INTERFACE_METHODS) {
                        events.add(
                            SimpleConditionEvent.violated(
                                javaClass,
                                String.format(
                                    "%s has %d abstract methods (max %d) - ISP violation",
                                    javaClass.getSimpleName(),
                                    methodCount,
                                    MAX_INTERFACE_METHODS
                                )
                            )
                        );
                    }
                }
            };

            ArchRule rule = classes()
                .that()
                .areInterfaces()
                .and()
                .resideInAPackage(BASE_PACKAGE + "..")
                .and()
                .resideOutsideOfPackage(GENERATED_GRAPHQL_PACKAGE)
                .should(haveLimitedMethods)
                .because("Interfaces should be small and focused (ISP)");

            rule.check(classes);
        }

        /**
         * SPI interfaces should be particularly focused.
         *
         * <p>Service Provider Interfaces define module contracts - they should be minimal.
         *
         * <p><b>Width = abstract + default instance methods.</b> Counting only {@code abstract}
         * methods would let an interface hide its true surface behind {@code default} no-op
         * bodies — a default no-op is still part of the contract every caller can invoke, so it
         * counts toward ISP width exactly like an abstract method. Static and private (helper)
         * methods are excluded — they are not part of the implementable contract.
         *
         * <p>Applies to every interface under {@code ..spi..} with no exemptions.
         */
        @Test
        void spiInterfacesAreFocused() {
            ArchCondition<JavaClass> beFocused = new ArchCondition<>(
                "have at most " + MAX_SPI_METHODS + " abstract+default methods"
            ) {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    int methodCount = (int) javaClass
                        .getMethods()
                        .stream()
                        // Implementable contract surface: every non-static, non-private instance
                        // method a caller can invoke or an implementer can override — abstract OR default.
                        .filter(m -> !m.getModifiers().contains(JavaModifier.STATIC))
                        .filter(m -> !m.getModifiers().contains(JavaModifier.PRIVATE))
                        .count();

                    if (methodCount > MAX_SPI_METHODS) {
                        events.add(
                            SimpleConditionEvent.violated(
                                javaClass,
                                String.format(
                                    "SPI %s has %d abstract+default methods (max %d) - split interface",
                                    javaClass.getSimpleName(),
                                    methodCount,
                                    MAX_SPI_METHODS
                                )
                            )
                        );
                    }
                }
            };

            ArchRule rule = classes()
                .that()
                .areInterfaces()
                .and()
                .resideInAPackage("..spi..")
                .should(beFocused)
                .allowEmptyShould(false) // strict: ..spi.. is populated; an empty match would mean the rule silently stopped seeing SPIs
                .because("SPI interfaces should be minimal");

            rule.check(classes);
        }
    }

    // DEPENDENCY INVERSION (merged from SolidPrinciplesTest)

    @Nested
    class DependencyInversionTests {

        /**
         * Limit ObjectProvider usage for lazy resolution / cycle breaking.
         *
         * <p>ObjectProvider is a valid way to break cycles, but should
         * be used sparingly. Known usages are documented here.
         */
        @Test
        void objectProviderUsageIsLimited() {
            Set<String> knownCycleBreakers = Set.of(
                "WorkspaceActivationService",
                "GithubLifecycleListener", // IntegrationNatsConsumer absent under the webhook runtime role (server.enabled=false) — see ADR 0008
                "WorkspaceLifecycleService", // IntegrationNatsConsumer absent under the webhook runtime role
                "GitHubWorkspaceProvisioningAdapter", // Lazy-loaded to break circular reference with GithubDataSyncService
                "WorkspaceRepositoryMonitorService",
                "GitLabWorkspaceInitializationService", // Optional GitLab beans gated by @ConditionalOnProperty
                "GitLabWebhookService", // Optional GitLab beans gated by @ConditionalOnProperty
                "GitlabDataSyncScheduler", // Optional GitLab beans gated by @ConditionalOnProperty
                "GitLabHistoricalBackfillService", // Optional GitLab beans gated by @ConditionalOnProperty
                "HistoricalBackfillScheduler", // Optional GitLab backfill service gated by @ConditionalOnProperty
                "AccountService", // PosthogClient is optional, gated by @ConditionalOnProperty(hephaestus.posthog.enabled=true)
                "GitHubWorkspaceDataSyncTrigger", // Lazy-loads GithubDataSyncService + SyncTargetProvider to break the same circular reference WorkspaceProvisioningAdapter handled; the workspace-side trigger sits on the GitHub adapter post-SPI extraction
                "WorkspaceScopedTables", // EntityManagerFactory is consumed transitively by HibernatePropertiesCustomizer — lazy lookup breaks the EMF<->tenancy startup cycle (see WorkspaceScopedTables javadoc)
                "MentorChatService" // InteractiveSandboxService is part of the worker capability (DockerSandboxConfiguration, gated on the worker role); absent on non-worker pods — resolved lazily at attach time
            );

            ArchCondition<JavaField> beInKnownClass = new ArchCondition<>("be in a known cycle-breaking class") {
                @Override
                public void check(JavaField field, ConditionEvents events) {
                    String ownerName = field.getOwner().getSimpleName();
                    if (!knownCycleBreakers.contains(ownerName)) {
                        events.add(
                            SimpleConditionEvent.violated(
                                field,
                                String.format(
                                    "NEW ObjectProvider usage in %s - add to knownCycleBreakers if intentional",
                                    field.getFullName()
                                )
                            )
                        );
                    }
                }
            };

            ArchRule rule = fields()
                .that()
                .haveRawType(org.springframework.beans.factory.ObjectProvider.class)
                .should(beInKnownClass)
                .because("ObjectProvider usage should be limited to documented cycle-breaking cases");

            rule.check(classes);
        }
    }

    // LISKOV SUBSTITUTION PRINCIPLE (merged from SolidPrinciplesTest)

    @Nested
    class LiskovSubstitutionTests {

        /**
         * Services should not declare generic Exception in methods.
         *
         * <p>LSP principle: methods should declare specific exceptions.
         */
        @Test
        void serviceMethodsDoNotDeclareGenericException() {
            ArchCondition<JavaClass> notDeclareGenericException = new ArchCondition<>(
                "not declare generic Exception in methods"
            ) {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    javaClass
                        .getMethods()
                        .stream()
                        .filter(m -> m.getOwner().equals(javaClass))
                        .filter(m -> !m.getName().startsWith("$"))
                        .filter(m -> !m.getName().equals("<init>"))
                        .forEach(method -> {
                            boolean declaresGenericException = method
                                .getThrowsClause()
                                .stream()
                                .anyMatch(t -> t.getRawType().getName().equals("java.lang.Exception"));
                            if (declaresGenericException) {
                                events.add(
                                    SimpleConditionEvent.violated(
                                        javaClass,
                                        String.format(
                                            "LSP: %s.%s declares generic Exception - use specific exceptions",
                                            javaClass.getSimpleName(),
                                            method.getName()
                                        )
                                    )
                                );
                            }
                        });
                }
            };

            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("Service")
                .and()
                .areAnnotatedWith(org.springframework.stereotype.Service.class)
                .should(notDeclareGenericException)
                .because("LSP: specific exceptions are required for substitutability");

            rule.check(classes);
        }

        /**
         * Service implementations should not throw UnsupportedOperationException.
         *
         * <p>LSP principle: a subtype should be substitutable for its supertype.
         * Throwing UnsupportedOperationException indicates the implementation doesn't
         * properly fulfill its contract - violating LSP.
         *
         * <p><strong>Detection method:</strong> This check scans method bytecode for
         * instantiation of UnsupportedOperationException, which catches both direct throws
         * and throws via utility methods.
         */
        @Test
        void serviceImplementationsDoNotThrowUnsupportedOperationException() {
            ArchCondition<JavaClass> notThrowUnsupportedOperationException = new ArchCondition<>(
                "not throw UnsupportedOperationException"
            ) {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    javaClass
                        .getMethods()
                        .stream()
                        .filter(m -> m.getOwner().equals(javaClass))
                        .filter(m -> !m.getName().startsWith("$"))
                        .filter(m -> !m.getName().startsWith("lambda$"))
                        .filter(m -> !m.getName().equals("<init>"))
                        .forEach(method -> {
                            // Check if method instantiates UnsupportedOperationException
                            boolean throwsUnsupported = method
                                .getConstructorCallsFromSelf()
                                .stream()
                                .anyMatch(call ->
                                    call.getTargetOwner().getName().equals("java.lang.UnsupportedOperationException")
                                );

                            if (throwsUnsupported) {
                                events.add(
                                    SimpleConditionEvent.violated(
                                        javaClass,
                                        String.format(
                                            "LSP: %s.%s throws UnsupportedOperationException - " +
                                                "indicates contract violation, consider interface redesign",
                                            javaClass.getSimpleName(),
                                            method.getName()
                                        )
                                    )
                                );
                            }
                        });
                }
            };

            ArchRule rule = classes()
                .that()
                .haveSimpleNameEndingWith("Service")
                .and()
                .areAnnotatedWith(org.springframework.stereotype.Service.class)
                .should(notThrowUnsupportedOperationException)
                .because("LSP: implementations must honor contracts, not refuse operations");

            rule.check(classes);
        }
    }
}
