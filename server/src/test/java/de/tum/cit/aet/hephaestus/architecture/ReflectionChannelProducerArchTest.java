package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import org.junit.jupiter.api.Test;

/**
 * Keeps the reflection lane's blast radius small enough to review.
 *
 * <p>What makes this lane worth a rule at all is that a REFLECTION body is the only text the system
 * composes about a named person that the person is the sole audience for — {@code IN_CONTEXT} bodies are
 * already public on the work, {@code CONVERSATION} bodies are NULL until the mentor speaks them. A class
 * that can put a unit on this channel, or take a body off it, is a class that can leak private text, so
 * that set should be small, named, and stable.
 *
 * <p><b>What the rule reaches.</b> Classes that name the {@link FeedbackChannel#REFLECTION} constant.
 * {@code FeedbackRepository} both writes and withholds the channel through the string literal
 * {@code 'REFLECTION'} inside native SQL, which no bytecode rule can see — which is why it carries no
 * allowlist entry here, and why its own queries are pinned by integration tests instead.
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
