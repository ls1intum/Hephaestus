package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import de.tum.cit.aet.hephaestus.core.AuditExempt;
import de.tum.cit.aet.hephaestus.core.Audited;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

/**
 * Every admin mutation endpoint must declare whether it is audited: {@link Audited} names the ledger
 * the change lands in, {@link AuditExempt} says why it deliberately lands in none. An endpoint that
 * declares neither is how a trail develops a hole nobody chose.
 */
class AuditByDefaultArchTest extends HephaestusArchitectureTest {

    private static final String INSTANCE_ADMIN_AUTHORITY = "app_admin";

    /** The one ledger whose rows this test can follow back through the call graph. */
    private static final String CONFIG_AUDIT_LEDGER = "config_audit";

    @Test
    void everyAdminMutationEndpointDeclaresItsAuditStatus() {
        List<String> undeclared = classes
            .stream()
            .filter(AuditByDefaultArchTest::isController)
            .flatMap(c -> c.getMethods().stream())
            .filter(AuditByDefaultArchTest::isMutation)
            .filter(AuditByDefaultArchTest::isAdminGated)
            .filter(m -> !m.isAnnotatedWith(Audited.class) && !m.isAnnotatedWith(AuditExempt.class))
            .map(m -> m.getOwner().getSimpleName() + "." + m.getName())
            .sorted()
            .toList();

        assertThat(undeclared)
            .as(
                """
                These admin mutation endpoints declare neither @Audited nor @AuditExempt. Decide: if the \
                action changes configuration or access, record it on the audit trail and mark it \
                @Audited("<ledger> <TOKEN>"); if it genuinely should not be recorded, mark it \
                @AuditExempt(reason="…"). An undeclared admin action is how an audit trail silently \
                develops holes."""
            )
            .isEmpty();
    }

    /**
     * Every {@code @Audited} value parses as {@code "<ledger>[ <TOKEN>]"} — see {@link Audited}.
     *
     * <p>This is what keeps the grammar from splitting again. It shipped three ways at once
     * ({@code "WORKSPACE_LLM_CONNECTION"}, {@code "connection_audit"},
     * {@code "auth_event LLM_CONNECTION_CREATED"}), which meant a reader could not tell from the
     * annotation which trail to go read. It also makes the entity-type spelling load-bearing: a typo'd
     * token used to be indistinguishable from "names some other ledger", which silently opted the
     * endpoint out of {@link #auditDeclarationsMatchTheCallGraph()}.
     */
    @Test
    void everyAuditedValueNamesAKnownLedgerAndRecord() {
        List<String> malformed = classes
            .stream()
            .filter(AuditByDefaultArchTest::isController)
            .flatMap(c -> c.getMethods().stream())
            .flatMap(m ->
                m
                    .tryGetAnnotationOfType(Audited.class)
                    .stream()
                    .flatMap(a ->
                        malformedReason(a.value())
                            .map(reason -> m.getOwner().getSimpleName() + "." + m.getName() + ": " + reason)
                            .stream()
                    )
            )
            .sorted()
            .toList();

        assertThat(malformed)
            .as(
                """
                These @Audited values do not parse as "<ledger>[ <TOKEN>]". The value must start with the \
                table the row lands in (config_audit / auth_event / connection_audit); an optional second \
                token must be a real constant of that ledger's enum. See the Audited javadoc."""
            )
            .isEmpty();
    }

    private static Optional<String> malformedReason(String value) {
        String[] parts = value.split(" ");
        if (parts.length > 2) {
            return Optional.of("'" + value + "' has more than a ledger and one token");
        }
        Set<String> vocabulary = LEDGER_VOCABULARIES.get(parts[0]);
        if (vocabulary == null) {
            return Optional.of("'" + parts[0] + "' is not a known ledger " + LEDGER_VOCABULARIES.keySet());
        }
        if (parts.length == 1) {
            // A bare ledger is only legal where that ledger has no fixed vocabulary to name.
            return vocabulary.isEmpty()
                ? Optional.empty()
                : Optional.of("'" + parts[0] + "' rows are typed — name the constant, e.g. \"" + parts[0] + " X\"");
        }
        return vocabulary.contains(parts[1])
            ? Optional.empty()
            : Optional.of("'" + parts[1] + "' is not a constant of the " + parts[0] + " vocabulary");
    }

    /**
     * Ledger name → the enum constants that ledger types its rows with. {@code connection_audit} maps
     * to an empty set on purpose: its {@code event_type} column is a free string composed at write
     * time, so there is no constant an annotation could name.
     */
    private static final Map<String, Set<String>> LEDGER_VOCABULARIES = Map.of(
        CONFIG_AUDIT_LEDGER,
        constantsOf(ConfigAuditEntityType.values()),
        "auth_event",
        constantsOf(AuthEvent.EventType.values()),
        "connection_audit",
        Set.of()
    );

    private static Set<String> constantsOf(Enum<?>[] constants) {
        return Arrays.stream(constants).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * An {@code @Audited} endpoint that reaches no recorder is a promise nothing keeps; an
     * {@code @AuditExempt} one that records anyway invites the next reader to delete a working audit
     * call. Both of those shipped here before the build checked them.
     *
     * <p>Only {@code config_audit} values are checked — the other two ledgers point outside
     * {@link ConfigAuditPort} and cannot be resolved from the call graph.
     */
    @Test
    void auditDeclarationsMatchTheCallGraph() {
        List<String> contradictions = classes
            .stream()
            .filter(AuditByDefaultArchTest::isController)
            .flatMap(c -> c.getMethods().stream())
            .map(AuditByDefaultArchTest::contradiction)
            .flatMap(Optional::stream)
            .sorted()
            .toList();

        assertThat(contradictions)
            .as(
                """
                These endpoints' audit declarations contradict what they actually do. Either wire the \
                producer, or change the declaration to say what is true."""
            )
            .isEmpty();
    }

    private static Optional<String> contradiction(JavaMethod method) {
        String name = method.getOwner().getSimpleName() + "." + method.getName();
        boolean records = reachesRecorder(method, new HashSet<>(), 0);
        if (method.isAnnotatedWith(AuditExempt.class) && records) {
            return Optional.of(name + " is @AuditExempt but reaches ConfigAuditPort.record");
        }
        boolean namesEntityType = method
            .tryGetAnnotationOfType(Audited.class)
            .map(a -> a.value().startsWith(CONFIG_AUDIT_LEDGER + " "))
            .orElse(false);
        if (namesEntityType && !records) {
            return Optional.of(name + " is @Audited but reaches no recorder");
        }
        return Optional.empty();
    }

    /** Depth-bounded so a cyclic service graph terminates; audit writes sit shallow behind a handler. */
    private static boolean reachesRecorder(JavaMethod method, Set<String> seen, int depth) {
        // Keyed on (method, depth): a method first reached at the depth limit must not cache a `false`
        // that short-circuits a shallower path to a real recorder.
        if (depth > MAX_CALL_DEPTH || !seen.add(method.getFullName() + "@" + depth)) {
            return false;
        }
        for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
            JavaClass target = call.getTargetOwner();
            if (target.getName().equals(ConfigAuditPort.class.getName()) && call.getName().equals("record")) {
                return true;
            }
            if (!target.getPackageName().startsWith("de.tum.cit.aet.hephaestus")) {
                continue;
            }
            for (JavaMethod resolved : call.getTarget().resolveMember().stream().toList()) {
                if (reachesRecorder(resolved, seen, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final int MAX_CALL_DEPTH = 6;

    static boolean isController(JavaClass clazz) {
        // Meta-annotated too: @WorkspaceScopedController composes @RestController, and every
        // workspace-admin surface uses it.
        return (
            clazz.isAnnotatedWith(org.springframework.web.bind.annotation.RestController.class) ||
            clazz.isMetaAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
        );
    }

    static boolean isMutation(JavaMethod method) {
        return (
            method.isAnnotatedWith(PostMapping.class) ||
            method.isAnnotatedWith(PutMapping.class) ||
            method.isAnnotatedWith(PatchMapping.class) ||
            method.isAnnotatedWith(DeleteMapping.class)
        );
    }

    /**
     * Admin-gated = a workspace admin/owner gate or the instance-admin authority, on the method OR its
     * controller. Class-level gates count: seven controllers declare the gate once on the class.
     */
    static boolean isAdminGated(JavaMethod method) {
        return (
            hasWorkspaceAdminGate(method) ||
            hasWorkspaceAdminGate(method.getOwner()) ||
            isInstanceAdminGated(method) ||
            isInstanceAdminGated(method.getOwner())
        );
    }

    private static boolean hasWorkspaceAdminGate(
        com.tngtech.archunit.core.domain.properties.HasAnnotations<?> element
    ) {
        return element
            .getAnnotations()
            .stream()
            .map(a -> a.getRawType().getSimpleName())
            .anyMatch(n -> n.equals("RequireAtLeastWorkspaceAdmin") || n.equals("RequireWorkspaceOwner"));
    }

    private static boolean isInstanceAdminGated(com.tngtech.archunit.core.domain.properties.HasAnnotations<?> element) {
        // Meta-annotated too, matching isController: a composed @InstanceAdmin annotation would
        // otherwise take its endpoints out of the rule entirely.
        return element
            .tryGetAnnotationOfType(PreAuthorize.class)
            .or(() ->
                element
                    .getAnnotations()
                    .stream()
                    .flatMap(a -> a.getRawType().tryGetAnnotationOfType(PreAuthorize.class).stream())
                    .findFirst()
            )
            .map(a -> a.value().contains(INSTANCE_ADMIN_AUTHORITY))
            .orElse(false);
    }
}
