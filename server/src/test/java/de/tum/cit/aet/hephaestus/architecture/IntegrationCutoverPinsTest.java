package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Pins the #1198 integration cutover so a future commit cannot silently re-introduce the
 * three regressions PE-C surfaced during the audit:
 *
 * <ol>
 *   <li>Re-introducing the {@code gitprovider} package (renamed to {@code integration.scm}
 *       in pass 16/stage 3).</li>
 *   <li>Re-adding per-vendor webhook routes ({@code /github}, {@code /gitlab}, {@code /slack},
 *       {@code /outline}) alongside the unified {@code /webhooks/{kind}} endpoint.</li>
 *   <li>Re-declaring the legacy denormalised columns on {@code Workspace} that the Connection
 *       registry now owns.</li>
 * </ol>
 *
 * <p>Each rule pins a contract that cost real iteration effort to establish. The failure
 * messages name the offending site so a regressing PR fails fast at CI time.
 */
class IntegrationCutoverPinsTest extends HephaestusArchitectureTest {

    /**
     * Forbid the legacy {@code ..gitprovider..} package from being re-introduced.
     *
     * <p>The package was renamed to {@code integration.scm} in pass 16/stage 3 of the #1198
     * branch ({@code 2488e48fa}). A blanket noClasses-resideIn rule is the cheapest way to
     * prevent a partial revert from re-creating the legacy package as a sibling.
     */
    @Test
    @DisplayName("no class resides in the legacy gitprovider package")
    void noClassResidesInGitProviderPackage() {
        ArchRule rule = noClasses()
            .should()
            .resideInAPackage("..gitprovider..")
            .because(
                "#1198 pass 16/stage 3 renamed gitprovider/ → integration.scm/. Re-introducing "
                    + "the legacy package would silently fork the module graph."
            );
        rule.check(classes);
    }

    /**
     * Forbid legacy per-vendor webhook ingress routes.
     *
     * <p>Only {@code WebhookController @PostMapping("/webhooks/{kind}")} should match webhook
     * payloads. The rule fires when a @{@link PostMapping} value is <em>exactly</em>
     * {@code /github}, {@code /gitlab}, {@code /slack}, or {@code /outline} — the legacy
     * top-level ingress paths that pass 16 removed. Nested paths under a different
     * class-level {@code @RequestMapping} (e.g. {@code WorkspaceRegistryController}'s
     * {@code POST /workspaces/gitlab/preflight}) are admin-API surfaces, not webhook
     * ingress, and remain permitted.
     */
    @Test
    @DisplayName("no legacy per-vendor webhook PostMapping route")
    void noLegacyVendorWebhookRoutes() {
        Set<String> legacyVendorRouteNames = Set.of("/github", "/gitlab", "/slack", "/outline");

        ArchCondition<JavaMethod> notDeclareLegacyVendorRoute = new ArchCondition<>(
            "not declare a legacy /github, /gitlab, /slack, or /outline @PostMapping route"
        ) {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (!method.isAnnotatedWith(PostMapping.class)) {
                    return;
                }
                PostMapping mapping = method.getAnnotationOfType(PostMapping.class);
                String[] candidates = mapping.value().length > 0 ? mapping.value() : mapping.path();
                for (String value : candidates) {
                    String trimmed = value == null ? "" : value.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    if (legacyVendorRouteNames.contains(trimmed)) {
                        events.add(
                            SimpleConditionEvent.violated(
                                method,
                                String.format(
                                    "%s declares legacy per-vendor webhook route '%s' — only "
                                        + "WebhookController.@PostMapping(\"/webhooks/{kind}\") is permitted",
                                    method.getFullName(),
                                    trimmed
                                )
                            )
                        );
                    }
                }
            }
        };

        ArchRule rule = methods()
            .that()
            .areAnnotatedWith(PostMapping.class)
            .should(notDeclareLegacyVendorRoute)
            .because(
                "#1198 cutover collapsed all vendor webhook receivers behind /webhooks/{kind}; "
                    + "re-adding /github, /gitlab, /slack, or /outline as a top-level ingress "
                    + "route would create a second, unverified webhook endpoint that bypasses "
                    + "the unified verification framework."
            );
        rule.check(classes);
    }

    /**
     * Forbid legacy denormalised connection columns from re-appearing on JPA entities
     * (specifically {@code Workspace}).
     *
     * <p>Pass 16/commit 3 deleted eleven JPA fields that the Connection registry now owns —
     * silently re-adding them on the entity would re-create the dual-source-of-truth bug the
     * audit surfaced (workspace row says one thing, connection row says another).
     *
     * <p>The rule scopes to {@code @Entity}-annotated classes only. DTOs that intentionally
     * accept these field names on the API surface (e.g. {@code CreateWorkspaceRequestDTO})
     * are out of scope — they translate user input into Connection rows server-side and
     * carry no schema footprint of their own.
     */
    @Test
    @DisplayName("Workspace entity does not re-declare legacy Connection-owned fields")
    void noLegacyFieldsOnWorkspace() {
        Set<String> forbiddenFieldNames = Set.of(
            "installationId",
            "personalAccessToken",
            "gitProviderMode",
            "slackToken",
            "slackSigningSecret",
            "leaderboardNotificationTeam",
            "leaderboardNotificationChannelId",
            "installationLinkedAt",
            "gitlabGroupId",
            "gitlabWebhookId",
            "serverUrl"
        );

        ArchCondition<com.tngtech.archunit.core.domain.JavaField> notCarryLegacyName = new ArchCondition<>(
            "not carry the name of a legacy Connection-owned column"
        ) {
            @Override
            public void check(com.tngtech.archunit.core.domain.JavaField field, ConditionEvents events) {
                if (forbiddenFieldNames.contains(field.getName())) {
                    events.add(
                        SimpleConditionEvent.violated(
                            field,
                            String.format(
                                "%s re-introduces a legacy Connection-owned field name on a JPA "
                                    + "entity — the Connection registry now owns this data. See "
                                    + "#1198 pass 16/commit 3.",
                                field.getFullName()
                            )
                        )
                    );
                }
            }
        };

        // Scope to @Entity classes in the workspace package only. DTOs (CreateWorkspaceRequestDTO,
        // WorkspaceDTO, GitLabPreflightRequestDTO, …) and context records (WorkspaceContext) MUST
        // keep these field names on the wire — they're how clients pass credentials in, and how
        // the server reports back which workspace owns which installation. The dual-source bug
        // only happens when these field names re-appear as JPA columns; the rule pins that
        // narrow regression and lets the API surface evolve independently.
        //
        // We compose the entity-AND-workspace-package filter via {@link DescribedPredicate#and}
        // — chaining two `.areDeclaredInClassesThat()` fluents on the ArchRule builder would
        // OR them, scooping up DTOs and context records the rule was never meant to touch.
        com.tngtech.archunit.base.DescribedPredicate<com.tngtech.archunit.core.domain.JavaClass> entityInWorkspace =
            com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("..workspace..")
                .and(com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith(jakarta.persistence.Entity.class));
        ArchRule rule = fields()
            .that()
            .areDeclaredInClassesThat(entityInWorkspace)
            .should(notCarryLegacyName)
            .because(
                "#1198 pass 16/commit 3 dropped eleven denormalised columns from the Workspace "
                    + "entity and moved their data into the Connection registry. Re-declaring "
                    + "those field names on a JPA entity in the workspace package would "
                    + "silently re-create the dual-source-of-truth bug."
            );
        rule.check(classes);
    }
}
