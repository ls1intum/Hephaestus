package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Optional;
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
 */
class NativeQueryCommentArchTest extends HephaestusArchitectureTest {

    /**
     * Fully-qualified so no compile-time dependency on the JPA annotation is added here; ArchUnit
     * matches annotation types by name off the bytecode.
     */
    private static final String QUERY_ANNOTATION = "org.springframework.data.jpa.repository.Query";

    @Test
    void noApostrophesInSqlLineCommentsOfQueries() {
        ArchRule rule = methods()
            .that(
                new DescribedPredicate<JavaMethod>("are annotated with @Query") {
                    @Override
                    public boolean test(JavaMethod method) {
                        return queryValue(method).isPresent();
                    }
                }
            )
            .should(
                new ArchCondition<JavaMethod>("not contain an apostrophe in any -- SQL comment") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        queryValue(method).ifPresent(query -> {
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
                                        "Apostrophe in a SQL line comment of @Query on " +
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

    /** The {@code value} attribute of {@code @Query}, absent when the query is declared some other way. */
    private static Optional<String> queryValue(JavaMethod method) {
        return method
            .getAnnotations()
            .stream()
            .filter(annotation -> annotation.getRawType().getName().equals(QUERY_ANNOTATION))
            .findFirst()
            .flatMap(annotation -> annotation.get("value"))
            .filter(String.class::isInstance)
            .map(String.class::cast);
    }
}
