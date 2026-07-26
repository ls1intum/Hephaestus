package de.tum.cit.aet.hephaestus.agent.usage;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Transient;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

/**
 * The ledger's append path is a hand-written native INSERT, and this is what keeps it honest.
 *
 * <p><b>Why the insert is not derived from the entity.</b> The append must be idempotent —
 * {@code ON CONFLICT (source_type, source_id, source_attempt) DO NOTHING}, whose return value tells
 * the recorder whether a redelivered attempt was absorbed. Neither Spring Data's {@code save()} nor
 * {@code EntityManager.persist()} can express that, and {@code save()} on an entity with an assigned
 * id would route through {@code merge()} and cost a SELECT on both hot paths (see
 * {@link LlmUsageEvent#getId()}). So the SQL stays hand-written.
 *
 * <p><b>What that costs, and what this test buys back.</b> One new ledger column otherwise means five
 * edits that nothing forces to agree: the entity, {@link LlmUsageInsert}, the INSERT's column list,
 * its VALUES list, and {@link LlmUsageRecorder}. This test chains the first four together, and the
 * fifth is chained by the compiler — {@code LlmUsageInsert} is a record, so a new component breaks
 * the recorder's canonical-constructor call until it supplies a value. Add a column to the entity
 * alone and this test names the column that would have been silently dropped on every write.
 *
 * <p>It is a unit test on purpose: a live-database test would only catch the subset of drift that
 * PostgreSQL rejects, and a column added as nullable is exactly the drift it would not catch.
 */
class LlmUsageInsertContractTest extends BaseUnitTest {

    /** Column list and VALUES list of {@code INSERT INTO … (cols) VALUES (vals)}. */
    private static final Pattern INSERT_SHAPE = Pattern.compile(
        "INSERT INTO llm_usage_event\\s*\\((?<columns>[^)]*)\\)\\s*VALUES\\s*\\((?<values>.*?)\\)\\s*ON CONFLICT",
        Pattern.DOTALL
    );

    /** A SpEL parameter of the form {@code :#{#event.someAccessor()}}. */
    private static final Pattern VALUE_ACCESSOR = Pattern.compile(":#\\{#event\\.(?<accessor>\\w+)\\(\\)}");

    @Test
    @DisplayName("the native insert carries every column the entity maps — no column can be added to one alone")
    void insertCoversEveryMappedColumn() {
        Set<String> entityColumns = mappedColumnsOf(LlmUsageEvent.class);
        Set<String> insertColumns = insertColumns();

        assertThat(insertColumns)
            .as(
                "a mapped column missing from the insert is written NULL (or defaulted) on every append, " +
                    "silently, for as long as it stays missing"
            )
            .containsExactlyInAnyOrderElementsOf(entityColumns);
    }

    @Test
    @DisplayName("each column is fed by the record component of the same name, in the same position")
    void eachColumnIsFedByTheComponentOfTheSameName() {
        List<String> columns = List.copyOf(insertColumns());
        List<String> accessors = valueAccessors();

        assertThat(accessors)
            .as("a column list and a VALUES list of different lengths does not reach PostgreSQL at all")
            .hasSameSizeAs(columns);
        for (int i = 0; i < columns.size(); i++) {
            assertThat(snakeCase(accessors.get(i)))
                .as(
                    "value %d is bound to column '%s' but reads %s#%s — two same-typed columns swapped " +
                        "this way write real money into the wrong place and nothing else would notice",
                    i + 1,
                    columns.get(i),
                    LlmUsageInsert.class.getSimpleName(),
                    accessors.get(i)
                )
                .isEqualTo(columns.get(i));
        }
    }

    @Test
    @DisplayName("the insert record carries no component the insert does not write")
    void everyRecordComponentIsWritten() {
        Set<String> components = Arrays.stream(LlmUsageInsert.class.getRecordComponents())
            .map(RecordComponent::getName)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        assertThat(new LinkedHashSet<>(valueAccessors()))
            .as("an unused component is a value the recorder computes and the ledger throws away")
            .containsExactlyInAnyOrderElementsOf(components);
    }

    /** Every column name {@code LlmUsageEvent} maps, read the way Hibernate reads it. */
    private static Set<String> mappedColumnsOf(Class<?> entity) {
        Set<String> columns = new LinkedHashSet<>();
        for (Field field : entity.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isAnnotationPresent(Transient.class)) {
                continue;
            }
            Column column = field.getAnnotation(Column.class);
            JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
            if (column != null && !column.name().isEmpty()) {
                columns.add(column.name());
            } else if (joinColumn != null && !joinColumn.name().isEmpty()) {
                columns.add(joinColumn.name());
            } else {
                // No explicit name: the property name is the column name, snake-cased.
                columns.add(snakeCase(field.getName()));
            }
        }
        return columns;
    }

    private static Set<String> insertColumns() {
        return Arrays.stream(insertShape().group("columns").split(","))
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<String> valueAccessors() {
        Matcher matcher = VALUE_ACCESSOR.matcher(insertShape().group("values"));
        return matcher
            .results()
            .map(result -> result.group(1))
            .toList();
    }

    private static Matcher insertShape() {
        Query query;
        try {
            query = LlmUsageEventRepository.class.getMethod("insertIfAbsent", LlmUsageInsert.class).getAnnotation(
                Query.class
            );
        } catch (NoSuchMethodException e) {
            throw new AssertionError("insertIfAbsent is the ledger's only append path; it must exist", e);
        }
        assertThat(query).as("the append must stay a native INSERT — see this class's Javadoc").isNotNull();
        Matcher matcher = INSERT_SHAPE.matcher(query.value());
        assertThat(matcher.find())
            .as("the append must stay INSERT INTO llm_usage_event (…) VALUES (…) ON CONFLICT …")
            .isTrue();
        return matcher;
    }

    /**
     * The convention the table's column names follow: underscores at case boundaries and before a
     * digit run, so {@code appliedPer1mInputUsd} reads {@code applied_per_1m_input_usd}. Hibernate's
     * own default strategy does not split before a digit, which is why all four rate columns declare
     * their name explicitly on the entity.
     */
    private static String snakeCase(String property) {
        return property
            .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
            .replaceAll("([a-zA-Z])([0-9])", "$1_$2")
            .toLowerCase(Locale.ROOT);
    }
}
