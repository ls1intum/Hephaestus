package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * An apostrophe inside a {@code --} line comment of a native {@code @Query} takes the whole
 * application down at startup.
 *
 * <p>Hibernate's parameter scanner does not understand SQL line comments. It sees the {@code '} in a
 * word like {@code sweep's}, treats it as the start of a quoted literal, finds no closing quote, and
 * fails the query with <em>"starts a quoted range at N, but never ends it"</em>. Spring Data cannot
 * then build the repository proxy, so <em>every</em> bean depending on it fails and the
 * {@code ApplicationContext} never starts — a whole-application outage caused by an English
 * possessive in a comment.
 *
 * <p>Nothing else catches this. Unit tests mock repositories, and the JPA slice/integration tests
 * that would boot the context are not what most contributors run first — the failure surfaces only
 * once something actually starts Spring, where it presents as an unrelated bean ("Error creating bean
 * with name 'achievementController'") many frames away from the offending comment. This has bitten
 * the codebase before. The rule is therefore mechanical: no apostrophes in SQL comments, ever.
 *
 * <p>Fix by rewording — {@code "the sweep's tombstone"} becomes {@code "a deletion-sweep tombstone"}.
 *
 * <p>The rule covers every annotation that can carry a hand-written query ({@code @Query},
 * {@code @NativeQuery}) and every attribute of those that can hold one ({@code value},
 * {@code countQuery}). A guard that covered only {@code @Query.value} would leave a one-annotation
 * bypass back to the same outage.
 */
class NativeQueryCommentArchTest extends HephaestusArchitectureTest {

    /**
     * Every annotation that can carry a hand-written query string. Fully-qualified so no
     * compile-time dependency on the JPA annotations is added here; ArchUnit matches annotation
     * types by name off the bytecode.
     *
     * <p>{@code @NativeQuery} is meta-annotated {@code @Query(nativeQuery = true)}, but ArchUnit
     * reports <em>directly declared</em> annotations only — matching {@code @Query} alone therefore
     * misses it entirely, and one {@code @NativeQuery} would reproduce the outage this rule exists
     * to prevent. It is the higher-risk of the two: it is native by definition, so every query it
     * carries is raw SQL that may contain {@code --} comments.
     */
    private static final Set<String> QUERY_ANNOTATIONS = Set.of(
        "org.springframework.data.jpa.repository.Query",
        "org.springframework.data.jpa.repository.NativeQuery"
    );

    /**
     * The attributes of those annotations that hold a query string. {@code countQuery} is a real
     * carrier, not a hypothetical: both annotations declare it, it is a separate hand-written
     * statement, and it fails at repository-proxy construction exactly like {@code value}. The
     * remaining attributes ({@code countProjection}, {@code name}, {@code countName},
     * {@code queryRewriter}) never hold SQL comments.
     */
    private static final Set<String> QUERY_ATTRIBUTES = Set.of("value", "countQuery");

    @Test
    void noApostrophesInSqlLineCommentsOfQueries() {
        ArchRule rule = methods()
            .that(
                new DescribedPredicate<JavaMethod>("are annotated with @Query or @NativeQuery") {
                    @Override
                    public boolean test(JavaMethod method) {
                        return !queryStrings(method).isEmpty();
                    }
                }
            )
            .should(
                new ArchCondition<JavaMethod>("not contain an apostrophe in any -- SQL comment") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        queryStrings(method).forEach((attribute, query) -> {
                            for (String line : query.split("\\R")) {
                                int commentStart = line.indexOf("--");
                                if (commentStart < 0) {
                                    continue;
                                }
                                String comment = line.substring(commentStart);
                                if (comment.indexOf('\'') < 0) {
                                    continue;
                                }
                                events.add(
                                    SimpleConditionEvent.violated(
                                        method,
                                        "Apostrophe in a SQL line comment of the '" +
                                            attribute +
                                            "' query on " +
                                            method.getFullName() +
                                            " — Hibernate reads it as an unterminated quoted range and the " +
                                            "ApplicationContext will fail to start. Reword the comment. Offending line: " +
                                            line.trim()
                                    )
                                );
                            }
                        });
                    }
                }
            )
            .allowEmptyShould(true);

        rule.check(classes);
    }

    /**
     * Every query string carried by the method, keyed by the attribute it came from. Empty when the
     * method declares no query annotation, or declares one whose query is defined some other way
     * (a named query, or a defaulted {@code countQuery}).
     */
    private static Map<String, String> queryStrings(JavaMethod method) {
        Map<String, String> found = new LinkedHashMap<>();
        method
            .getAnnotations()
            .stream()
            .filter(annotation -> QUERY_ANNOTATIONS.contains(annotation.getRawType().getName()))
            .forEach(annotation -> {
                for (String attribute : QUERY_ATTRIBUTES) {
                    annotation
                        .get(attribute)
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .filter(query -> !query.isBlank())
                        .ifPresent(query -> found.put(attribute, query));
                }
            });
        return found;
    }
}
