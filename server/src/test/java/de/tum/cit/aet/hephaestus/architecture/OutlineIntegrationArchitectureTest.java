package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Structural invariants of the Outline footprint — the {@code integration.outline} module plus its two
 * agent-side outposts ({@code agent.documentation} and {@code agent.context.providers.OutlineDocumentContentSource})
 * — that a careless commit would otherwise break silently. Sibling of
 * {@link SlackIntegrationArchitectureTest}, holding Outline to the same bar:
 *
 * <ul>
 *   <li>every REST controller in the module is workspace-scoped (or an {@code @Hidden} inbound receiver, should
 *       one ever appear) — an unscoped Outline endpoint would bypass tenancy authorization;</li>
 *   <li>every Outline NATS consumer extends the core envelope-handler base — a hand-rolled handler would skip the
 *       shared deserialization/subject discipline (the one deliberate exception is individually allowlisted with
 *       its reason, see {@link #HANDLER_BASE_ALLOWLIST});</li>
 *   <li>every Outline repository finder carries the workspace predicate in its signature — the compile-time
 *       complement to the runtime tenancy {@code StatementInspector} (the one deliberately unscoped
 *       fleet-enumeration query is allowlisted and its caller pinned {@code @WorkspaceAgnostic});</li>
 *   <li>no class in the footprint reaches for raw {@code JdbcTemplate} SQL — DB queries belong in Spring Data
 *       repositories, not hand-rolled JDBC tucked inside a {@code @Component} (unlike Slack, Outline has no
 *       pre-scoping tenant-resolution exception, so the rule holds with an empty allowlist); and</li>
 *   <li>a class whose name promises a stereotype ({@code *Service}/{@code *Repository}/{@code *Controller}) is not
 *       lying about it — directional on purpose (see {@link #outlineServiceNamesAreServices()}).</li>
 * </ul>
 */
class OutlineIntegrationArchitectureTest extends HephaestusArchitectureTest {

    private static final String OUTLINE = "de.tum.cit.aet.hephaestus.integration.outline..";

    private static final String OUTLINE_CONTENT_SOURCE =
        "de.tum.cit.aet.hephaestus.agent.context.providers.OutlineDocumentContentSource";

    /**
     * The whole Outline footprint: the integration module itself, the agent-side projection SPI package it
     * implements ({@code agent.documentation}), and the agent context provider that reads the mirror
     * ({@code OutlineDocumentContentSource}, matched by name prefix so nested classes count too).
     */
    private static final DescribedPredicate<JavaClass> OUTLINE_FOOTPRINT = DescribedPredicate.describe(
        "belong to the Outline footprint (integration.outline, agent.documentation, OutlineDocumentContentSource)",
        javaClass ->
            javaClass.getPackageName().startsWith("de.tum.cit.aet.hephaestus.integration.outline") ||
            javaClass.getPackageName().startsWith("de.tum.cit.aet.hephaestus.agent.documentation") ||
            javaClass.getName().startsWith(OUTLINE_CONTENT_SOURCE)
    );

    @Test
    @DisplayName("every Outline REST controller is workspace-scoped or a hidden webhook receiver")
    void outlineControllersAreWorkspaceScopedOrHiddenReceivers() {
        // Selection is meta-annotation aware because @WorkspaceScopedController IS the @RestController
        // meta-annotation here — a controller carrying only the meta-annotation must still be caught if it
        // is ever swapped for a bare @RestController without workspace scoping.
        classes()
            .that()
            .resideInAPackage(OUTLINE)
            .and()
            .areMetaAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .should(
                new ArchCondition<>("be @WorkspaceScopedController or @Hidden") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        boolean workspaceScoped = javaClass.isAnnotatedWith(
                            "de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController"
                        );
                        boolean hiddenReceiver = javaClass.isAnnotatedWith("io.swagger.v3.oas.annotations.Hidden");
                        if (!workspaceScoped && !hiddenReceiver) {
                            events.add(
                                SimpleConditionEvent.violated(
                                    javaClass,
                                    javaClass.getName() +
                                        " is an Outline @RestController that is neither @WorkspaceScopedController" +
                                        " nor an @Hidden webhook receiver"
                                )
                            );
                        }
                    }
                }
            )
            .check(classes);
    }

    /**
     * The one Outline consumer allowed to implement {@code IntegrationMessageHandler} directly instead of
     * extending {@code AbstractIntegrationMessageHandler}: all Outline events collapse onto a single logical
     * event key ({@code OutlineWebhookMessageHandler.EVENT_TYPE}) while the wire subject still carries the
     * specific event name, so the base's exact-equality last-subject-segment validation (one handler = one
     * subject token) cannot hold; the handler also deliberately treats the payload as routing-only (no typed
     * DTO), skipping the base's Jackson deserialization on purpose. Any NEW Outline handler must extend the
     * base or earn its own justified entry here.
     */
    private static final Set<String> HANDLER_BASE_ALLOWLIST = Set.of("OutlineWebhookMessageHandler");

    @Test
    @DisplayName("every Outline NATS consumer extends the core envelope-handler base (or is allowlisted)")
    void outlineMessageHandlersExtendTheSharedBase() {
        classes()
            .that()
            .resideInAPackage(OUTLINE)
            .and()
            .haveSimpleNameEndingWith("MessageHandler")
            .and()
            .doNotHaveModifier(JavaModifier.ABSTRACT)
            .should(
                new ArchCondition<>("be assignable to the core integration handler base (or allowlisted contract)") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        boolean coreBase = javaClass.isAssignableTo(
                            "de.tum.cit.aet.hephaestus.integration.core.handler.AbstractIntegrationMessageHandler"
                        );
                        // Allowlisted handlers still must honour the shared consumer contract.
                        boolean allowlistedContract =
                            HANDLER_BASE_ALLOWLIST.contains(javaClass.getSimpleName()) &&
                            javaClass.isAssignableTo(
                                "de.tum.cit.aet.hephaestus.integration.core.handler.IntegrationMessageHandler"
                            );
                        if (!coreBase && !allowlistedContract) {
                            events.add(
                                SimpleConditionEvent.violated(
                                    javaClass,
                                    javaClass.getName() +
                                        " is an Outline *MessageHandler outside AbstractIntegrationMessageHandler" +
                                        " and not an allowlisted IntegrationMessageHandler implementation"
                                )
                            );
                        }
                    }
                }
            )
            .check(classes);
    }

    /**
     * Deliberately unscoped repository methods: fleet-wide enumeration a scheduler must run before it can scope
     * to a workspace. Their callers carry {@code @WorkspaceAgnostic} (pinned by
     * {@link #fleetEnumeratorCallerIsWorkspaceAgnostic()}); everything else must take a workspace id.
     */
    private static final Set<String> UNSCOPED_ALLOWLIST = Set.of(
        "OutlineCollectionRepository.findDistinctWorkspaceIdsWithPendingSync"
    );

    @Test
    @DisplayName("Outline repository finders carry the workspace predicate in their signature")
    void outlineRepositoryFindersAreWorkspaceScopedAtCompileTime() {
        classes()
            .that()
            .resideInAPackage("de.tum.cit.aet.hephaestus.integration.outline.domain..")
            .and()
            .areInterfaces()
            .and()
            .haveSimpleNameEndingWith("Repository")
            .should(
                new ArchCondition<>("declare only workspace-scoped finders (or allowlisted enumerators)") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        for (JavaMethod method : javaClass.getMethods()) {
                            if (!method.getOwner().equals(javaClass)) {
                                continue;
                            }
                            String name = method.getName();
                            if (!name.matches("(find|count|delete|exists).*")) {
                                continue;
                            }
                            if (UNSCOPED_ALLOWLIST.contains(javaClass.getSimpleName() + "." + name)) {
                                continue;
                            }
                            boolean scoped = nameCarriesWorkspaceParam(method);
                            if (!scoped) {
                                events.add(
                                    SimpleConditionEvent.violated(
                                        method,
                                        javaClass.getSimpleName() +
                                            "." +
                                            name +
                                            " has no workspaceId parameter and is not allowlisted"
                                    )
                                );
                            }
                        }
                    }
                }
            )
            .check(classes);
    }

    /**
     * A finder counts as workspace-scoped when its derived-query name references WorkspaceId or its (annotated)
     * parameter list contains a workspaceId. Spring Data derived names make the name check the reliable signal;
     * {@code @Param}-annotated native/JPQL methods are covered by the parameter scan.
     */
    private static boolean nameCarriesWorkspaceParam(JavaMethod method) {
        // "ByWorkspaceId" (not bare "WorkspaceId") so fleet enumerators like
        // findDistinctWorkspaceIdsWithPendingSync do NOT count as scoped — they must go through the allowlist.
        if (method.getName().contains("ByWorkspaceId")) {
            return true;
        }
        return method
            .getParameters()
            .stream()
            .anyMatch(p ->
                p
                    .getAnnotations()
                    .stream()
                    .anyMatch(
                        a ->
                            a.getRawType().getName().equals("org.springframework.data.repository.query.Param") &&
                            a
                                .get("value")
                                .map(v -> "workspaceId".equals(v))
                                .orElse(false)
                    )
            );
    }

    @Test
    @DisplayName("the workspace-scope detector accepts scoped shapes and rejects the unscoped enumerator")
    void workspaceScopeDetectorSelfTest() {
        // The rule above delegates to nameCarriesWorkspaceParam; if that helper rotted (e.g. the @Param annotation
        // lookup broke), every finder would count as unscoped — the allowlist and the rule's real violations would
        // be indistinguishable from noise. This pins both scoped shapes (derived name + @Param) and the negative
        // on the live class graph.
        JavaClass documentRepo = classes
            .stream()
            .filter(c -> c.getSimpleName().equals("OutlineDocumentRepository"))
            .findFirst()
            .orElseThrow();
        JavaClass collectionRepo = classes
            .stream()
            .filter(c -> c.getSimpleName().equals("OutlineCollectionRepository"))
            .findFirst()
            .orElseThrow();
        JavaMethod scopedDerived = documentRepo
            .getMethods()
            .stream()
            .filter(m -> m.getName().equals("findByWorkspaceIdAndConnectionIdAndDocumentId"))
            .findFirst()
            .orElseThrow();
        JavaMethod scopedByParamAnnotation = collectionRepo
            .getMethods()
            .stream()
            .filter(m -> m.getName().equals("findForSync"))
            .findFirst()
            .orElseThrow();
        JavaMethod unscopedEnumerator = collectionRepo
            .getMethods()
            .stream()
            .filter(m -> m.getName().equals("findDistinctWorkspaceIdsWithPendingSync"))
            .findFirst()
            .orElseThrow();
        assertThat(nameCarriesWorkspaceParam(scopedDerived)).isTrue();
        assertThat(nameCarriesWorkspaceParam(scopedByParamAnnotation)).isTrue();
        // The enumerator is unscoped BY DESIGN — the helper must say so, forcing it through the explicit allowlist.
        assertThat(nameCarriesWorkspaceParam(unscopedEnumerator)).isFalse();
    }

    @Test
    @DisplayName("the allowlisted fleet enumerator's caller is pinned @WorkspaceAgnostic")
    void fleetEnumeratorCallerIsWorkspaceAgnostic() {
        // findDistinctWorkspaceIdsWithPendingSync earns its UNSCOPED_ALLOWLIST entry only because its caller
        // declares the tenancy bypass explicitly. If the scheduler ever dropped the annotation (or was renamed
        // without updating this pin), the justification would silently rot — fail loudly instead.
        classes()
            .that()
            .haveFullyQualifiedName("de.tum.cit.aet.hephaestus.integration.outline.sync.OutlineDocumentSyncScheduler")
            .should()
            .beAnnotatedWith("de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic")
            .because(
                "the scheduler runs the allowlisted fleet enumeration " +
                    "(OutlineCollectionRepository.findDistinctWorkspaceIdsWithPendingSync) before any tenant is " +
                    "bound, so it must declare the bypass via @WorkspaceAgnostic"
            )
            .check(classes);
    }

    @Test
    @DisplayName("no Outline class depends on raw JdbcTemplate — DB queries belong in repositories")
    void outlineClassesDoNotUseRawJdbcTemplate() {
        // Unlike Slack (whose SlackWorkspaceResolver must query BEFORE workspace scoping exists), Outline resolves
        // its tenant through the core ConnectionService — so this rule holds with NO allowlist. Keep it that way.
        ArchRule rule = noClasses()
            .that(OUTLINE_FOOTPRINT)
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate")
            .because(
                "DB queries belong in Spring Data repositories (JPQL/native @Query), not raw JdbcTemplate SQL " +
                    "hand-rolled inside a @Component — Outline has no pre-scoping tenant-resolution exception, " +
                    "so there is deliberately no allowlist here"
            );
        rule.check(classes);
    }

    /**
     * Directional stereotype-suffix discipline: a name that PROMISES a stereotype must not lie about it.
     *
     * <p>Deliberately NOT the reverse direction ("every {@code @Service} must be named {@code *Service}"): the
     * footprint has a legitimate "zoo" of {@code @Component} names by role — {@code *Scheduler}, {@code *Projector},
     * {@code *Resolver}, {@code *Registrar}, {@code *Verifier}, {@code *Handler}, {@code *Source}, … — and forcing
     * all of them into one suffix would be a mass rename with no correctness payoff. What matters is that
     * {@code *Service}/{@code *Repository}/{@code *Controller} — names that make a concrete stereotype promise to
     * a reader — keep that promise. Mirrors the Slack rule; Outline starts with an empty exception list.
     */
    @Test
    @DisplayName("*Service/*Repository/*Controller names keep their stereotype promise")
    void outlineServiceNamesAreServices() {
        classes()
            .that(OUTLINE_FOOTPRINT)
            .and()
            .haveSimpleNameEndingWith("Service")
            .should(
                new ArchCondition<>("be annotated @Service (or be an interface)") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        boolean isService = javaClass.isAnnotatedWith("org.springframework.stereotype.Service");
                        if (!isService && !javaClass.isInterface()) {
                            events.add(
                                SimpleConditionEvent.violated(
                                    javaClass,
                                    javaClass.getName() + " is named *Service but is not annotated @Service"
                                )
                            );
                        }
                    }
                }
            )
            .check(classes);

        classes()
            .that(OUTLINE_FOOTPRINT)
            .and()
            .haveSimpleNameEndingWith("Repository")
            .should(
                new ArchCondition<>("be an interface extending org.springframework.data.repository.Repository") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        boolean isRepositoryInterface =
                            javaClass.isInterface() &&
                            javaClass.isAssignableTo("org.springframework.data.repository.Repository");
                        if (!isRepositoryInterface) {
                            events.add(
                                SimpleConditionEvent.violated(
                                    javaClass,
                                    javaClass.getName() +
                                        " is named *Repository but is not an interface extending Repository"
                                )
                            );
                        }
                    }
                }
            )
            .check(classes);

        classes()
            .that(OUTLINE_FOOTPRINT)
            .and()
            .haveSimpleNameEndingWith("Controller")
            .and()
            // an abstract *Controller base (shared plumbing for concrete controllers) makes no runtime promise
            .doNotHaveModifier(JavaModifier.ABSTRACT)
            .should(
                new ArchCondition<>("be @RestController, directly or via a meta-annotation") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        boolean isRestController = javaClass.isMetaAnnotatedWith(
                            "org.springframework.web.bind.annotation.RestController"
                        );
                        if (!isRestController) {
                            events.add(
                                SimpleConditionEvent.violated(
                                    javaClass,
                                    javaClass.getName() + " is named *Controller but is not @RestController"
                                )
                            );
                        }
                    }
                }
            )
            .check(classes);
    }
}
