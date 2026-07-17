package de.tum.cit.aet.hephaestus.core.audit;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditAction;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditActorKind;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pins the Java constants to the changelog they are duplicated in.
 *
 * <p>Necessary because the test tier runs {@code ddl-auto: create} with Liquibase disabled, so no test
 * ever executes this changelog — a value that drifts from its SQL counterpart would otherwise surface
 * only in production. {@code auth_event} already lived through this: its CHECK constraint fell behind
 * its enum and needed changeset {@code 1782980500800-15} to catch up.
 *
 * <p>Both duplications fail silently, which is why they are pinned rather than documented:
 *
 * <ul>
 *   <li>An enum value missing from its CHECK lands an INSERT that raises at runtime; an extra one
 *       lets a value into a row that is append-only and therefore unrepairable.</li>
 *   <li>A retention window shorter in Java than in the trigger makes every sweep raise, so retention
 *       dies unnoticed; longer, and rows over-retain while the docs still claim the window.</li>
 * </ul>
 */
@Tag("unit")
class ConfigAuditChangelogParityTest {

    private static final Path CHANGELOG = Path.of("src/main/resources/db/changelog/1784242879917_changelog.xml");

    @Test
    void entityTypeEnumMatchesItsCheckConstraint() {
        assertThat(checkConstraintValues("ck_config_audit_event_entity_type")).containsExactlyInAnyOrderElementsOf(
            names(ConfigAuditEntityType.values())
        );
    }

    @Test
    void actionEnumMatchesItsCheckConstraint() {
        assertThat(checkConstraintValues("ck_config_audit_event_action")).containsExactlyInAnyOrderElementsOf(
            names(ConfigAuditAction.values())
        );
    }

    @Test
    void actorKindEnumMatchesItsCheckConstraint() {
        assertThat(checkConstraintValues("ck_config_audit_event_actor_kind")).containsExactlyInAnyOrderElementsOf(
            names(ConfigAuditActorKind.values())
        );
    }

    @Test
    void retentionWindowMatchesTheTriggersInterval() {
        Matcher m = Pattern.compile("interval '(\\d+) days'").matcher(changelog());
        assertThat(m.find()).as("retention interval present in the immutability trigger").isTrue();
        assertThat(Integer.parseInt(m.group(1)))
            .as("ConfigAuditRetentionJob.RETENTION_DAYS must match the trigger's DELETE carve-out")
            .isEqualTo(ConfigAuditRetentionJob.RETENTION_DAYS);
    }

    private static List<String> checkConstraintValues(String constraintName) {
        Matcher m = Pattern.compile(
            Pattern.quote(constraintName) + "\\s*\\n?\\s*CHECK \\([a-z_]+ IN \\(([^)]*)\\)\\)"
        ).matcher(changelog());
        assertThat(m.find()).as("CHECK constraint %s present in the changelog", constraintName).isTrue();
        return Arrays.stream(m.group(1).split(","))
            .map(v -> v.trim().replace("'", ""))
            .filter(v -> !v.isEmpty())
            .toList();
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    private static String changelog() {
        try {
            return Files.readString(CHANGELOG, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("could not read " + CHANGELOG.toAbsolutePath(), e);
        }
    }
}
