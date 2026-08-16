package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;

/**
 * Keeps the in-app lane's blast radius small enough to review.
 *
 * <p>What makes this lane worth a rule at all is that an IN_APP body is the only text the system
 * composes about a named person that the person is the sole audience for — {@code IN_CONTEXT} bodies are
 * already public on the work, {@code IN_CHAT} bodies are NULL until the mentor speaks them. A class
 * that can put a unit on this channel, or take a body off it, is a class that can leak private text, so
 * that set should be small, named, and stable.
 *
 * <p><b>What the rule reaches.</b> Classes that name the {@link FeedbackChannel#IN_APP} constant.
 * {@code FeedbackRepository} both writes and withholds the channel through the string literal
 * {@code 'IN_APP'} inside native SQL, which no bytecode rule can see — which is why it carries no
 * allowlist entry here, and why its own queries are pinned by integration tests instead.
 *
 * <p><b>Why the allowlist is names and not a narrower rule.</b> Naming the constant is a proxy for
 * producing a body, and a deliberately loose one: a rule that only caught, say, a class that both names
 * the constant and touches the {@code Feedback} entity would miss the obvious way around it — hand
 * {@code IN_APP} to something else and let that write the row. Being loose is what makes the rule
 * worth having, and it is affordable because the cost of a false positive is one line here plus the
 * sentence that justifies it. {@link #theCompositionSeamCanWriteNoFeedbackAtAll()} is what keeps that
 * cost honest for the one entry whose reason is "declares, never produces".
 */
class InAppChannelProducerArchTest extends HephaestusArchitectureTest {

    /** The producer package: routes composed messages onto the lane and writes the units. */
    private static final String PRODUCER_PACKAGE = "..agent.handler.inapp..";

    /** The reader package: the recipient's own surface, plus the layout of the stored body. */
    private static final String READER_PACKAGE = "..practices.feedback.inapp..";

    /**
     * Named as a string because the class is package-private — which is itself the point: the operator
     * query that withholds the body has no API surface, so nothing can route around it.
     */
    private static final String OPERATOR_WITHHOLDER =
        "de.tum.cit.aet.hephaestus.practices.reviewoutput.ReviewFeedbackQueryService";

    /** The shared seam upstream of all three lanes; it stages their input and parses their output. */
    private static final String COMPOSITION_PACKAGE = "..agent.handler.composition..";

    /**
     * Tells one run which lanes are open for it, which means enumerating every lane there is — a lane
     * left unnamed would read to the composer as a lane the system does not have.
     */
    private static final String CAPABILITY_DECLARATION =
        "de.tum.cit.aet.hephaestus.agent.handler.composition.FeedbackCompositionInputs";

    @Test
    void onlyTheLanesOwnPackagesNameTheInAppChannel() {
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
            .and()
            // Declares which lanes one run may write for. It names all three and writes to none of them,
            // which is not this rule's concern but is only credible because the rule below holds.
            .haveNameNotMatching(CAPABILITY_DECLARATION + "(\\$.*)?")
            .should()
            .accessField(FeedbackChannel.class, "IN_APP")
            .because(
                "an IN_APP body is the only feedback text whose sole audience is the person it is about, " +
                    "so the classes that can produce one or read one back stay named and few: the producer " +
                    "in agent.handler.inapp, the recipient's own surface in practices.feedback.inapp, " +
                    "the enum that declares the constant, the class that derives the provenance " +
                    "entitlement, the operator query that withholds the body, and the composition seam that " +
                    "declares which lanes are open for a run without being able to write to any of them. " +
                    "A new name here is a new way for private text to be written or exposed, and it belongs " +
                    "in one of those places"
            );
        rule.check(classes);
    }

    /**
     * The composition seam names every lane, so its exemption above is only safe while it remains unable
     * to act on the one it names. That is what this pins.
     *
     * <p>Composition is a translation between the job's payload and the lanes: it stages the bounds the
     * model is given and reads back what the model proposed. It decides nothing and stores nothing —
     * {@code FeedbackCompositionResultParser} deliberately checks a unit against the payload the composer
     * was shown rather than against a re-query, and each lane's own router still runs afterwards. So the
     * seam needs neither the ledger's entity nor any repository, and while it has neither it cannot put a
     * unit on the in-app lane or read a stored body back off it, whatever constant it names.
     *
     * <p>Deliberately every repository rather than the feedback ones: the honest statement about this
     * package is that it touches no storage at all, and a rule that lists only the feedback repositories
     * would invite a class here to reach the ledger through some other one.
     */
    @Test
    void theCompositionSeamCanWriteNoFeedbackAtAll() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage(COMPOSITION_PACKAGE)
            .should()
            .dependOnClassesThat()
            .areAssignableTo(Feedback.class)
            .orShould()
            .dependOnClassesThat()
            .areAssignableTo(Repository.class)
            .because(
                "the composition seam is allowed to name the in-app channel because it only declares " +
                    "which lanes are open for a run; it stays unable to produce feedback on any of them, " +
                    "and holding no feedback entity and no repository is what makes that structural rather " +
                    "than a promise. A class here that needs to write a unit or read one back is a lane " +
                    "producer, and belongs in the lane's own package where the rule above can see it"
            );
        rule.check(classes);
    }
}
