package de.tum.cit.aet.hephaestus.core.auth.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@code auth_event.event_type} is constrained at the database by {@code ck_auth_event_event_type},
 * whose admitted values are spelled out in a Liquibase changelog. Adding a constant to
 * {@link AuthEvent.EventType} without widening that CHECK produces a defect no other test tier can
 * see: the suite builds its schema with {@code ddl-auto: create} and so never applies the constraint,
 * and the audit write runs in its own transaction — so in production the insert is rejected, the
 * failure is logged rather than propagated, and the audited action commits with no audit row. An
 * audit trail that silently develops holes is exactly what the {@code @Audited} rule exists to
 * prevent, so the two lists are pinned to each other here.
 */
@Tag("unit")
class AuthEventTypeConstraintParityTest {

    private static final Path MASTER = Path.of("src/main/resources/db/master.xml");

    private static final Pattern INCLUDE = Pattern.compile("<include\\s+file=\"([^\"]+)\"");

    /** The `event_type IN ( … )` list of the most recently defined CHECK wins, as replays apply in order. */
    private static final Pattern CHECK_CONSTRAINT = Pattern.compile(
        "ADD\\s+CONSTRAINT\\s+ck_auth_event_event_type\\s+CHECK\\s*\\(\\s*event_type\\s+IN\\s*\\((.*?)\\)\\s*\\)",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    private static final Pattern QUOTED = Pattern.compile("'([A-Z_]+)'");

    @Test
    void everyEventTypeConstantIsAdmittedByTheDatabaseConstraint() throws IOException {
        Set<String> admitted = admittedByLatestConstraint();
        Set<String> declared = Arrays.stream(AuthEvent.EventType.values())
            .map(Enum::name)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        assertThat(admitted)
            .as("no changelog defines ck_auth_event_event_type — has the constraint been renamed?")
            .isNotEmpty();
        assertThat(declared)
            .as(
                "these AuthEvent.EventType constants are not admitted by ck_auth_event_event_type, so writing " +
                    "one would be rejected by Postgres and the audited action would commit unaudited — widen the " +
                    "CHECK in a new changelog"
            )
            .isSubsetOf(admitted);
    }

    /**
     * Walks {@code master.xml}'s include list in reverse — the order Liquibase actually applies, which
     * the filename order is only conventionally aligned with. A changelog on disk but absent from
     * {@code master.xml} therefore cannot satisfy this test, because it never reaches a database either.
     */
    private static Set<String> admittedByLatestConstraint() throws IOException {
        String master = Files.readString(MASTER, StandardCharsets.UTF_8);
        Matcher include = INCLUDE.matcher(master);
        List<String> included = new ArrayList<>();
        while (include.find()) {
            included.add(include.group(1));
        }
        Path changelogDirectory = MASTER.getParent();
        org.junit.jupiter.api.Assertions.assertNotNull(changelogDirectory);
        for (String changelog : included.reversed()) {
            Path path = changelogDirectory.resolve(changelog).normalize();
            if (!Files.exists(path)) {
                continue;
            }
            Matcher constraint = CHECK_CONSTRAINT.matcher(Files.readString(path, StandardCharsets.UTF_8));
            String lastInFile = null;
            while (constraint.find()) {
                lastInFile = constraint.group(1);
            }
            if (lastInFile != null) {
                Set<String> values = new LinkedHashSet<>();
                Matcher value = QUOTED.matcher(lastInFile);
                while (value.find()) {
                    values.add(value.group(1));
                }
                return values;
            }
        }
        return Set.of();
    }
}
