package de.tum.cit.aet.hephaestus.agent.usage;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Transient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.jpa.repository.Query;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * The ledger's append path is a hand-written native INSERT, and this is what keeps it honest.
 *
 * <p><b>Why the insert is not derived from the entity.</b> The append must be idempotent —
 * {@code ON CONFLICT (source_type, source_id, source_attempt) DO NOTHING}, whose return value tells
 * the recorder whether a redelivered attempt was absorbed. Neither Spring Data's {@code save()} nor
 * {@code EntityManager.persist()} can express that, and {@code save()} on an entity with an assigned
 * id would route through {@code merge()} and cost a SELECT on both hot paths. So the SQL stays
 * hand-written.
 *
 * <p><b>What that costs, and what this test buys back.</b> One new ledger column otherwise means edits
 * that nothing forces to agree: the Liquibase changelog, the entity, {@link LlmUsageInsert}, the
 * INSERT's column list and its VALUES list. Only {@link LlmUsageRecorder} is chained by the compiler,
 * since {@code LlmUsageInsert} is a record and a new component breaks the recorder's
 * canonical-constructor call until it supplies a value.
 *
 * <p><b>The table is the arbiter, not a second guess at it.</b> Both the insert and the entity are
 * compared against the column list the committed changelogs actually create, because that list IS the
 * table: when the entity and the SQL disagree about a column name it is the database that decides who
 * is wrong. Deriving the expectation from the entity instead would mean re-implementing Hibernate's
 * implicit naming strategy here and asserting this file's guess.
 *
 * <p>It is a unit test on purpose: a live-database test would only catch the subset of drift that
 * PostgreSQL rejects, and a column added as nullable is exactly the drift it would not catch.
 */
class LlmUsageInsertContractTest extends BaseUnitTest {

    private static final String TABLE = "llm_usage_event";

    private static final String CHANGELOGS = "classpath*:db/changelog/*.xml";

    /** Column list and VALUES list of {@code INSERT INTO … (cols) VALUES (vals)}. */
    private static final Pattern INSERT_SHAPE = Pattern.compile(
            "INSERT INTO llm_usage_event\\s*\\((?<columns>[^)]*)\\)\\s*VALUES\\s*\\((?<values>.*?)\\)\\s*ON CONFLICT",
            Pattern.DOTALL);

    /** A SpEL parameter of the form {@code :#{#event.someAccessor()}}. */
    private static final Pattern VALUE_ACCESSOR = Pattern.compile(":#\\{#event\\.(?<accessor>\\w+)\\(\\)}");

    /**
     * A column added or removed by raw SQL rather than by a Liquibase {@code addColumn}/{@code dropColumn}
     * tag — invisible to the reader below, so it has to stop the build rather than skew the expectation.
     */
    private static final Pattern RAW_SQL_COLUMN_CHANGE =
            Pattern.compile("(?i)alter\\s+table\\s+(?:\\w+\\.)?" + TABLE + "\\s+(?:add|drop)\\s+column");

    @Test
    @DisplayName("the native insert carries every column the table has — no column can be added to one alone")
    void insertCoversEveryColumnTheTableHas() {
        assertThat(insertColumns())
                .as("a column the table has but the insert omits is written NULL (or defaulted) on every append, "
                        + "silently, for as long as it stays missing")
                .containsExactlyInAnyOrderElementsOf(committedTableColumns());
    }

    @Test
    @DisplayName("the entity maps every column the table has, and none the table does not")
    void entityMapsExactlyTheColumnsTheTableHas() {
        assertThat(mappedColumnsOf(LlmUsageEvent.class))
                .as("a mapped column the table does not have fails at the first read of the ledger, in production")
                .containsExactlyInAnyOrderElementsOf(committedTableColumns());
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
            assertThat(columnNameOf(accessors.get(i)))
                    .as(
                            "value %d is bound to column '%s' but reads %s#%s — two same-typed columns swapped "
                                    + "this way write real money into the wrong place and nothing else would notice",
                            i + 1, columns.get(i), LlmUsageInsert.class.getSimpleName(), accessors.get(i))
                    .isEqualTo(columns.get(i));
        }
    }

    @Test
    @DisplayName("the insert record carries no component the insert does not write")
    void everyRecordComponentIsWritten() {
        Set<String> components = Arrays.stream(LlmUsageInsert.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(new LinkedHashSet<>(valueAccessors()))
                .as("an unused component is a value the recorder computes and the ledger throws away")
                .containsExactlyInAnyOrderElementsOf(components);
    }

    /**
     * Replays every committed changelog's {@code createTable}/{@code addColumn}/{@code dropColumn}/
     * {@code renameColumn} for {@link #TABLE}, in the order an operator's database applies them
     * (filenames are epoch-millisecond timestamps, so lexical order is chronological order).
     */
    private static Set<String> committedTableColumns() {
        Set<String> columns = new LinkedHashSet<>();
        for (Resource changelog : changelogsInApplyOrder()) {
            applyChangelog(changelog, columns);
        }
        assertThat(columns)
                .as("no committed changelog creates %s — the reader above is looking in the wrong place", TABLE)
                .isNotEmpty();
        return columns;
    }

    /**
     * A {@code <rollback>} states how to undo a change, not what the table holds. Its {@code dropColumn}
     * describes removing the column the same changeSet just added, so replaying it would cancel the add
     * and leave the reader believing a shipped column does not exist.
     */
    private static boolean insideRollback(Element element) {
        for (Node node = element.getParentNode(); node != null; node = node.getParentNode()) {
            if ("rollback".equals(node.getNodeName())) {
                return true;
            }
        }
        return false;
    }

    private static List<Resource> changelogsInApplyOrder() {
        Resource[] found;
        try {
            found = new PathMatchingResourcePatternResolver().getResources(CHANGELOGS);
        } catch (IOException e) {
            throw new AssertionError("the committed changelogs must be readable from the classpath", e);
        }
        return Arrays.stream(found)
                .sorted(Comparator.comparing(resource -> Objects.requireNonNull(resource.getFilename())))
                .toList();
    }

    private static void applyChangelog(Resource changelog, Set<String> columns) {
        byte[] bytes;
        try {
            bytes = changelog.getContentAsByteArray();
        } catch (IOException e) {
            throw new AssertionError("cannot read changelog " + changelog.getFilename(), e);
        }
        assertThat(RAW_SQL_COLUMN_CHANGE
                        .matcher(new String(bytes, StandardCharsets.UTF_8))
                        .find())
                .as(
                        "%s changes a %s column with raw SQL; this reader only understands Liquibase's own "
                                + "addColumn/dropColumn tags, so express the change with those",
                        changelog.getFilename(), TABLE)
                .isFalse();

        NodeList elements = parse(bytes, changelog).getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (!TABLE.equals(element.getAttribute("tableName")) || insideRollback(element)) {
                continue;
            }
            switch (element.getNodeName()) {
                case "createTable", "addColumn" -> columns.addAll(namedChildColumns(element));
                case "dropColumn" -> {
                    if (!element.getAttribute("columnName").isEmpty()) {
                        columns.remove(element.getAttribute("columnName"));
                    }
                    columns.removeAll(namedChildColumns(element));
                }
                case "renameColumn" -> {
                    columns.remove(element.getAttribute("oldColumnName"));
                    columns.add(element.getAttribute("newColumnName"));
                }
                default -> {
                    // Indexes, constraints and data changes do not move the column list.
                }
            }
        }
    }

    private static Set<String> namedChildColumns(Element parent) {
        Set<String> names = new LinkedHashSet<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element column && "column".equals(column.getNodeName())) {
                String name = column.getAttribute("name");
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private static Document parse(byte[] bytes, Resource changelog) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new AssertionError("cannot parse changelog " + changelog.getFilename(), e);
        }
    }

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
                // No declared name: the column name is then whatever the implicit naming strategy makes
                // of the field name, which this test must not guess. A single all-lowercase word is the
                // one shape every strategy maps to itself, so that is the shape an undeclared name has
                // to have — anything else declares @Column(name = "…") and says so.
                assertThat(field.getName())
                        .as(
                                "%s#%s must declare @Column(name = \"…\"): its column name is strategy-dependent",
                                entity.getSimpleName(), field.getName())
                        .matches("[a-z]+");
                columns.add(field.getName());
            }
        }
        return columns;
    }

    private static Set<String> insertColumns() {
        return Arrays.stream(insertShape().group("columns").split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<String> valueAccessors() {
        Matcher matcher = VALUE_ACCESSOR.matcher(insertShape().group("values"));
        return matcher.results().map(result -> result.group(1)).toList();
    }

    private static Matcher insertShape() {
        Query query;
        try {
            query = LlmUsageEventRepository.class
                    .getMethod("insertIfAbsent", LlmUsageInsert.class)
                    .getAnnotation(Query.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("insertIfAbsent is the ledger's only append path; it must exist", e);
        }
        Objects.requireNonNull(query, "the append must stay a native @Query INSERT — see this class's Javadoc");
        Matcher matcher = INSERT_SHAPE.matcher(query.value());
        assertThat(matcher.find())
                .as("the append must stay INSERT INTO llm_usage_event (…) VALUES (…) ON CONFLICT …")
                .isTrue();
        return matcher;
    }

    /**
     * The naming convention {@link LlmUsageInsert} declares for itself — one component per column,
     * named after it: underscores at case boundaries and before a digit run, so
     * {@code appliedPer1mInputUsd} reads {@code applied_per_1m_input_usd}.
     */
    private static String columnNameOf(String component) {
        return component
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("([a-zA-Z])([0-9])", "$1_$2")
                .toLowerCase(Locale.ROOT);
    }
}
