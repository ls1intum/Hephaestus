package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import org.junit.jupiter.api.Test;

/**
 * Keeps the reflection lane's blast radius small enough to review.
 *
 * <p>This rule replaces {@code ReflectionChannelUnwrittenArchTest}, whose stated reason — "no producer
 * writes a REFLECTION feedback unit" — stopped being true the moment one did. A guard whose justification
 * is false is worse than no guard, because the next person reads the reason rather than the code and
 * exempts themselves from a rule they think is vestigial. So the rule is inverted rather than extended:
 * it no longer says the lane is unwritten, it says <em>who</em> may write it.
 *
 * <p>What makes this lane worth a rule at all is not that it is new. It is that a REFLECTION body is the
 * only text the system composes about a named person that the person is the sole audience for —
 * {@code IN_CONTEXT} bodies are already public on the work, {@code CONVERSATION} bodies are NULL until
 * the mentor speaks them. Every class that can put a unit on this channel, or take a body off it, is
 * therefore a class that can leak private text, and that set should be small, named, and stable.
 */
class ReflectionChannelProducerArchTest extends HephaestusArchitectureTest {

    /** The producer package: routes composed messages onto the lane and writes the units. */
    private static final String PRODUCER_PACKAGE = "..agent.handler.reflection..";

    /** The reader package: the recipient's own surface, plus the layout of the stored body. */
    private static final String READER_PACKAGE = "..practices.feedback.reflection..";

    /**
     * Named as a string because the class is package-private — which is itself the point: the operator
     * query that withholds the body has no API surface, so nothing can route around it.
     */
    private static final String OPERATOR_WITHHOLDER =
        "de.tum.cit.aet.hephaestus.practices.reviewoutput.ReviewFeedbackQueryService";

    @Test
    void onlyTheLanesOwnPackagesNameTheReflectionChannel() {
        ArchRule rule = noClasses()
            .that()
            .resideOutsideOfPackage(PRODUCER_PACKAGE)
            .and()
            .resideOutsideOfPackage(READER_PACKAGE)
            .and()
            // An enum's <clinit> and synthetic $values() cannot avoid touching its own constant.
            .haveNameNotMatching(FeedbackChannel.class.getName() + "(\\$.*)?")
            .and()
            // Derives which provenance may be said out loud on which channel.
            .haveNameNotMatching(ObservationOrigin.class.getName() + "(\\$.*)?")
            .and()
            // Withholds the body from the operator surfaces; it must name the channel to withhold it.
            .haveNameNotMatching(OPERATOR_WITHHOLDER + "(\\$.*)?")
            .should()
            .accessField(FeedbackChannel.class, "REFLECTION")
            .because(
                "a REFLECTION body is the only feedback text whose sole audience is the person it is about, " +
                    "so the classes that can produce one or read one back stay named and few: the producer " +
                    "in agent.handler.reflection, the recipient's own surface in practices.feedback.reflection, " +
                    "the enum that declares the constant, the class that derives the provenance " +
                    "entitlement, and the operator query that withholds the body. A new name here is a new " +
                    "way for private text to be written or exposed, and it belongs in one of those places"
            );
        rule.check(classes);
    }
}
