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
 * Pins the Java constants to the changelog that duplicates them. Necessary because the test tier runs
 * {@code ddl-auto: create} with Liquibase disabled, so no test executes this changelog and a value that
 * drifts from its SQL counterpart surfaces only in production — {@code auth_event} already lived
 * through exactly that.
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
        // A constraint may be defined once and later redefined (DROP+ADD in a follow-up changeSet), so
        // the effective value set is the LAST forward definition. Deliberately not "the widest": that
        // cannot see a NARROWING, which is the drift that breaks production — Java keeps emitting a
        // value the CHECK no longer accepts. <rollback> bodies are skipped; they re-add older, narrower
        // definitions that never run forward.
        String forwardOnly = ROLLBACK.matcher(changelog()).replaceAll("");
        Matcher m = Pattern.compile(Pattern.quote(constraintName) + "\\s*CHECK \\([a-z_]+ IN \\(([^)]*)\\)\\)").matcher(
            forwardOnly
        );
        List<String> latest = List.of();
        while (m.find()) {
            latest = Arrays.stream(m.group(1).split(","))
                .map(v -> v.trim().replace("'", ""))
                .filter(v -> !v.isEmpty())
                .toList();
        }
        assertThat(latest).as("CHECK constraint %s present in the changelog", constraintName).isNotEmpty();
        return latest;
    }

    private static final Pattern ROLLBACK = Pattern.compile("<rollback>.*?</rollback>", Pattern.DOTALL);

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
