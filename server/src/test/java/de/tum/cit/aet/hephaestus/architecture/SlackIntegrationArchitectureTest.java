package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Structural invariants of the {@code integration.slack} module that a careless commit would otherwise break
 * silently:
 *
 * <ul>
 *   <li>every REST controller in the module is either workspace-scoped or an {@code @Hidden} inbound webhook
 *       receiver (an explicitly allowlisted user-scoped controller aside) — an unscoped Slack endpoint would
 *       bypass tenancy authorization;</li>
 *   <li>every NATS consumer follows one of the two envelope-handler bases — a hand-rolled handler would skip the
 *       shared deserialization/teamId discipline (and, for the channel path, the transaction boundary);</li>
 *   <li>every Slack repository finder carries the workspace predicate in its signature — the compile-time
 *       complement to the runtime tenancy {@code StatementInspector} (deliberately unscoped fleet-enumeration
 *       queries are individually allowlisted and guarded by {@code @WorkspaceAgnostic} callers).</li>
 * </ul>
 *
 * <p>The manifest ↔ handler parity check lives with the manifest test
 * ({@code integration.slack.webhook.SlackManifestTemplateTest}) because it reads the YAML template, not classes.
 */
class SlackIntegrationArchitectureTest extends HephaestusArchitectureTest {

    private static final String SLACK = "de.tum.cit.aet.hephaestus.integration.slack..";

    /**
     * User-scoped (not workspace-scoped) controllers, allowed by name: the cross-workspace preferences read is
     * keyed on the authenticated account, not a workspace path.
     */
    private static final Set<String> USER_SCOPED_CONTROLLERS = Set.of("SlackUserPreferencesUserController");

    @Test
    @DisplayName("every Slack REST controller is workspace-scoped or a hidden webhook receiver")
    void slackControllersAreWorkspaceScopedOrHiddenReceivers() {
        classes()
            .that()
            .resideInAPackage(SLACK)
            .and()
            .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .and(
                DescribedPredicate.describe("are not explicitly allowlisted user-scoped controllers", javaClass ->
                    !USER_SCOPED_CONTROLLERS.contains(javaClass.getSimpleName())
                )
            )
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
                                        " is a Slack @RestController that is neither @WorkspaceScopedController nor" +
                                        " an @Hidden webhook receiver"
                                )
                            );
                        }
                    }
                }
            )
            .check(classes);
    }

    @Test
    @DisplayName("every Slack NATS consumer extends one of the two envelope-handler bases")
    void slackMessageHandlersExtendTheSharedBases() {
        // Two bases by design: AbstractSlackEnvelopeHandler for plain envelope routing, and the core
        // AbstractIntegrationMessageHandler for the channel-message path, which additionally needs the shared
        // TransactionTemplate boundary around its DB-writing ingest.
        classes()
            .that()
            .resideInAPackage("de.tum.cit.aet.hephaestus.integration.slack.webhook..")
            .and()
            .haveSimpleNameEndingWith("MessageHandler")
            .and()
            .doNotHaveModifier(JavaModifier.ABSTRACT)
            .should(
                new ArchCondition<>("be assignable to a Slack/integration envelope-handler base") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        boolean slackBase = javaClass.isAssignableTo(
                            "de.tum.cit.aet.hephaestus.integration.slack.webhook.AbstractSlackEnvelopeHandler"
                        );
                        boolean coreBase = javaClass.isAssignableTo(
                            "de.tum.cit.aet.hephaestus.integration.core.handler.AbstractIntegrationMessageHandler"
                        );
                        if (!slackBase && !coreBase) {
                            events.add(
                                SimpleConditionEvent.violated(
                                    javaClass,
                                    javaClass.getName() + " is a Slack *MessageHandler outside both handler bases"
                                )
                            );
                        }
                    }
                }
            )
            .check(classes);
    }

    /**
     * Deliberately unscoped repository methods: fleet-wide enumeration a scheduler must run before it can scope to
     * a workspace. Their callers carry {@code @WorkspaceAgnostic}; everything else must take a workspace id.
     */
    private static final Set<String> UNSCOPED_ALLOWLIST = Set.of(
        "SlackMessageRepository.findDistinctWorkspaceIds",
        "SlackMonitoredChannelRepository.findDistinctWorkspaceIdsByConsentState"
    );

    @Test
    @DisplayName("Slack repository finders carry the workspace predicate in their signature")
    void slackRepositoryFindersAreWorkspaceScopedAtCompileTime() {
        classes()
            .that()
            .resideInAPackage("de.tum.cit.aet.hephaestus.integration.slack.domain..")
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
        // "ByWorkspaceId" (not bare "WorkspaceId") so fleet enumerators like findDistinctWorkspaceIdsByConsentState
        // do NOT count as scoped — they must go through the explicit allowlist.
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
    @DisplayName("the workspace-scope detector accepts scoped shapes and rejects an unscoped finder")
    void workspaceScopeDetectorSelfTest() {
        // The rule above delegates to nameCarriesWorkspaceParam; if that helper rotted (e.g. the @Param annotation
        // lookup broke), every finder would count as unscoped — the allowlist and the rule's real violations would
        // be indistinguishable from noise. This pins both directions cheaply on the live class graph.
        JavaClass repo = classes
            .stream()
            .filter(c -> c.getSimpleName().equals("SlackMonitoredChannelRepository"))
            .findFirst()
            .orElseThrow();
        JavaMethod scopedDerived = repo
            .getMethods()
            .stream()
            .filter(m -> m.getName().equals("findByWorkspaceIdAndSlackChannelId"))
            .findFirst()
            .orElseThrow();
        JavaMethod unscopedEnumerator = repo
            .getMethods()
            .stream()
            .filter(m -> m.getName().equals("findDistinctWorkspaceIdsByConsentState"))
            .findFirst()
            .orElseThrow();
        assertThat(nameCarriesWorkspaceParam(scopedDerived)).isTrue();
        // The enumerator is unscoped BY DESIGN — the helper must say so, forcing it through the explicit allowlist.
        assertThat(nameCarriesWorkspaceParam(unscopedEnumerator)).isFalse();
    }
}
