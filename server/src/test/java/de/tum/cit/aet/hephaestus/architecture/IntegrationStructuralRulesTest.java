package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Structural fitness functions added in the Phase 4 integration restructure (PR #1306).
 *
 * <p>Each rule pins an invariant the PE audit identified — together they make the
 * vendor-agnostic SPI, vendor adapter boundaries, and the {@code integration/} top-level
 * shape enforceable at build time so a future commit cannot silently re-introduce the
 * coupling Phases 1–4 removed.
 *
 * <h2>Rule index</h2>
 * <ol>
 *   <li>{@link #scmDomainDoesNotDependOnVendorAdapters} — shared SCM kernel must not
 *       know about GitHub or GitLab.</li>
 *   <li>{@link #spiHasNoVendorLiteralIdentifiers} — SPI surface holds no class name with
 *       a vendor literal in it.</li>
 *   <li>{@link #coreConsumerHasNoVendorLiteralIdentifiers} — consumer wiring holds no
 *       class, field, or method name with a vendor literal in it.</li>
 *   <li>{@link #coreEventsDoesNotImportScmDomainEntities} — DISABLED, see Javadoc on
 *       the test. The events package re-exports SCM JPA entities via {@code .from(entity)}
 *       factory imports; lifting all 16 entity imports requires a deeper refactor of the
 *       payload-construction API (callers would need to hand-build value objects rather
 *       than passing the entity).</li>
 *   <li>{@link #integrationTopLevelHasOnlyExpectedSubpackages} — directly assert the
 *       filesystem layout: {@code integration/} children are exactly the four expected
 *       sub-roots plus the package-info.</li>
 *   <li>{@link #vendorAdaptersDoNotCrossImportEachOther} — belt-and-suspenders pin
 *       under the {@code scm.} prefix; complements the existing
 *       {@code IntegrationSpiBoundariesTest.kindModulesDoNotImportEachOther}.</li>
 * </ol>
 */
class IntegrationStructuralRulesTest extends HephaestusArchitectureTest {

    private static final Set<String> VENDOR_LITERALS = Set.of("Github", "Gitlab", "Slack", "Outline");

    @Test
    @DisplayName("integration.scm.domain does NOT depend on any vendor adapter")
    void scmDomainDoesNotDependOnVendorAdapters() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage("..integration.scm.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..integration.scm.github..", "..integration.scm.gitlab..")
            .because(
                "The SCM shared kernel must remain vendor-neutral. Vendor adapters write " +
                    "INTO the kernel via processors, not the reverse. A reverse dep collapses " +
                    "the per-vendor encapsulation Phases 2–3 established."
            );
        rule.check(classes);
    }

    @Test
    @DisplayName("integration.core.spi has no class simple-name containing a vendor literal")
    void spiHasNoVendorLiteralIdentifiers() {
        ArchRule rule = classes()
            .that()
            .resideInAPackage("..integration.core.spi..")
            .should(hasNoVendorLiteralInSimpleName())
            .because(
                "The unified SPI must be vendor-neutral by name. Phase 4's purge renamed " +
                    "GithubAppCredential → InstallationCredential and AuthMode.GITHUB_APP → " +
                    "INSTALLATION_APP. New vendor literals here mean the SPI is leaking back " +
                    "into vendor-specific concerns — add the new behaviour to a per-vendor SPI " +
                    "adapter, not the cross-vendor contract."
            );
        rule.check(classes);
    }

    @Test
    @DisplayName("integration.core.consumer has no class/field/method identifier with a vendor literal")
    void coreConsumerHasNoVendorLiteralIdentifiers() {
        ArchRule classRule = classes()
            .that()
            .resideInAPackage("..integration.core.consumer..")
            .should(hasNoVendorLiteralInSimpleName())
            .because(
                "Consumer wiring must be vendor-neutral. Pre-Phase-4 this had " +
                    "GITHUB_STREAM and installationFilterGithub() — renamed to " +
                    "INSTALLATION_AWARE_KIND and installationAwareSubjectFilter(IntegrationKind)."
            );
        ArchRule fieldRule = fields()
            .that()
            .areDeclaredInClassesThat()
            .resideInAPackage("..integration.core.consumer..")
            .should(fieldHasNoVendorLiteralInName())
            .because("Vendor-literal field names re-pin the consumer to a specific provider.");
        ArchRule methodRule = methods()
            .that()
            .areDeclaredInClassesThat()
            .resideInAPackage("..integration.core.consumer..")
            .should(methodHasNoVendorLiteralInName())
            .because("Vendor-literal method names re-pin the consumer to a specific provider.");
        classRule.check(classes);
        fieldRule.check(classes);
        methodRule.check(classes);
    }

    /**
     * Disabled because {@code EventPayload} imports 16 SCM domain entities solely to
     * accept them in static {@code .from(entity)} factories. Lifting the entity imports
     * requires an API change to {@code EventPayload.*Data.from(...)} — every caller in
     * scm/github/* and scm/gitlab/* processors would need to hand-construct DTO records
     * instead of handing an entity to a value-object factory (or move the {@code .from()}
     * factories to a converter layer inside scm/{github,gitlab}). That is a 30+ file
     * refactor with measurable risk and zero behavioural change — out of scope for
     * Phase 4. The rule is added here so a reader can see the gap; un-disable it in the
     * follow-up that moves the factories.
     */
    @Test
    @Disabled(
        "DEFERRED: see Javadoc. Lifting the 16 SCM entity imports out of EventPayload is a " +
            "30+ file refactor of vendor processors — out of scope for Phase 4. Re-enable after " +
            "moving .from(entity) factories to a per-vendor converter layer."
    )
    @DisplayName("integration.core.events does NOT import integration.scm.domain entities (DEFERRED)")
    void coreEventsDoesNotImportScmDomainEntities() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage("..integration.core.events..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..integration.scm.domain..")
            .because(
                "Events should carry value-object DTOs, not JPA entities. Today EventPayload " +
                    "imports Issue/PullRequest/User/etc to construct snapshots via static " +
                    ".from(entity). Move the factories vendor-side to clear this."
            );
        rule.check(classes);
    }

    /**
     * Filesystem-level assertion that {@code integration/} contains exactly the four
     * expected sub-roots plus the {@code package-info.java}. A new top-level under
     * integration/ would mean a fifth structural concern outside the {core, scm, slack,
     * outline} taxonomy Phases 1–4 settled on. If you legitimately need one, update this
     * set and document the rationale on the new top-level's {@code package-info.java}.
     */
    @Test
    @DisplayName("integration/ immediate children are exactly {core, scm, slack, outline, package-info.java}")
    void integrationTopLevelHasOnlyExpectedSubpackages() throws IOException {
        Path integrationDir = locateIntegrationRoot();
        try (Stream<Path> children = Files.list(integrationDir)) {
            Set<String> actual = children.map(p -> p.getFileName().toString()).collect(Collectors.toSet());
            Set<String> expected = Set.of("core", "scm", "slack", "outline", "package-info.java");
            assertThat(actual)
                .as(
                    "Phase 4 settled on {core, scm, slack, outline} as the only top-level integration " +
                        "sub-roots. Adding a fifth indicates a structural concern outside the established " +
                        "taxonomy — extend this expected set with a rationale rather than just letting it pass."
                )
                .isEqualTo(expected);
        }
    }

    /**
     * Belt-and-suspenders pin for the {@code IntegrationSpiBoundariesTest.kindModulesDoNotImportEachOther}
     * rule that Phase 2 already enforces. Phase 4 verified the rule still holds with the
     * scm/{github,gitlab} restructure — this test is intentionally a thin assertion that
     * the existing pinning still classifies under the {@code scm.} prefix.
     */
    @Test
    @DisplayName("vendor adapters under scm/ do not cross-import each other (regression pin)")
    void vendorAdaptersDoNotCrossImportEachOther() {
        ArchRule githubGitlab = noClasses()
            .that()
            .resideInAPackage("..integration.scm.github..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..integration.scm.gitlab..")
            .because("scm.github MUST NOT import scm.gitlab — cross-vendor coupling defeats the SPI.");
        ArchRule gitlabGithub = noClasses()
            .that()
            .resideInAPackage("..integration.scm.gitlab..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..integration.scm.github..")
            .because("scm.gitlab MUST NOT import scm.github — cross-vendor coupling defeats the SPI.");
        githubGitlab.check(classes);
        gitlabGithub.check(classes);
    }

    // -------------------------------------------------------------------------
    // ArchUnit predicates
    // -------------------------------------------------------------------------

    private static ArchCondition<JavaClass> hasNoVendorLiteralInSimpleName() {
        return new ArchCondition<JavaClass>(
            "have no vendor literal (Github/Gitlab/Slack/Outline) in the simple class name"
        ) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String name = item.getSimpleName();
                for (String literal : VENDOR_LITERALS) {
                    if (name.contains(literal)) {
                        events.add(
                            SimpleConditionEvent.violated(
                                item,
                                "Class " + item.getFullName() + " contains vendor literal '" + literal + "'"
                            )
                        );
                        return;
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaField> fieldHasNoVendorLiteralInName() {
        return new ArchCondition<JavaField>("have no vendor literal in the field name") {
            @Override
            public void check(JavaField item, ConditionEvents events) {
                String literal = findVendorLiteral(item.getName());
                if (literal != null) {
                    events.add(
                        SimpleConditionEvent.violated(
                            item,
                            "Field " + item.getFullName() + " contains vendor literal '" + literal + "'"
                        )
                    );
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> methodHasNoVendorLiteralInName() {
        return new ArchCondition<JavaMethod>("have no vendor literal in the method name") {
            @Override
            public void check(JavaMethod item, ConditionEvents events) {
                String literal = findVendorLiteral(item.getName());
                if (literal != null) {
                    events.add(
                        SimpleConditionEvent.violated(
                            item,
                            "Method " + item.getFullName() + " contains vendor literal '" + literal + "'"
                        )
                    );
                }
            }
        };
    }

    /**
     * Returns the first vendor literal found in {@code identifier} (case-insensitive), or
     * {@code null} if none match. Treats "github", "Github", and "GITHUB" all as
     * violations so {@code GITHUB_STREAM}, {@code githubStream}, and {@code GithubStream}
     * are all caught.
     */
    private static String findVendorLiteral(String identifier) {
        String lowered = identifier.toLowerCase(Locale.ROOT);
        for (String literal : VENDOR_LITERALS) {
            if (lowered.contains(literal.toLowerCase(Locale.ROOT))) {
                return literal;
            }
        }
        return null;
    }

    private static Path locateIntegrationRoot() {
        Path serverDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path integrationDir = serverDir.resolve("src/main/java/de/tum/cit/aet/hephaestus/integration");
        if (!Files.isDirectory(integrationDir)) {
            // Some IDEs run with the parent dir as user.dir.
            integrationDir = serverDir.resolve("server/src/main/java/de/tum/cit/aet/hephaestus/integration");
        }
        if (!Files.isDirectory(integrationDir)) {
            throw new IllegalStateException(
                "Could not locate integration/ source root from user.dir=" + serverDir
            );
        }
        return integrationDir;
    }
}
