package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackReach;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import org.junit.jupiter.api.Test;

/**
 * Makes "declared but unwritten" enforceable rather than merely commented.
 *
 * <p>{@code FeedbackReach.reaches(PROFILE)} returns {@code false} at every reach and
 * {@code ObservationOrigin.BACKFILL} is entitled to {@code PROFILE} and nothing else, so a backfilled
 * observation reaches no channel anybody can write to today — recorded as
 * {@code FeedbackSuppressionReason.BACKFILL_QUIET} rather than swallowed. {@code FeedbackAdmissionTest} pins
 * the entitlement matrix, but a matrix cannot see a new {@code Feedback.builder().channel(PROFILE)} call site
 * appearing elsewhere; this rule can.
 */
class ProfileChannelUnwrittenArchTest extends HephaestusArchitectureTest {

    /**
     * The three allowed namers, and why each is not a producer:
     * <ul>
     *   <li>{@code FeedbackChannel} — an enum cannot declare a constant without its {@code <clinit>} and
     *       synthetic {@code $values()} touching it.</li>
     *   <li>{@code ObservationOrigin} and {@code FeedbackReach} — they derive the entitlement described above;
     *       {@code FeedbackReach} is matched by prefix because {@code javac} compiles its {@code switch} into a
     *       synthetic {@code FeedbackReach$1} holding the switch map, and that synthetic is where the field
     *       access lands.</li>
     * </ul>
     */
    @Test
    void nothingButTheEntitlementDerivationNamesTheProfileChannel() {
        ArchRule rule = noClasses()
            .that()
            .haveNameNotMatching(FeedbackChannel.class.getName() + "(\\$.*)?")
            .and()
            .haveNameNotMatching(ObservationOrigin.class.getName() + "(\\$.*)?")
            .and()
            .haveNameNotMatching(FeedbackReach.class.getName() + "(\\$.*)?")
            .should()
            .accessField(FeedbackChannel.class, "PROFILE")
            .because(
                "no producer writes a PROFILE feedback unit, so FeedbackReach.reaches(PROFILE) is false at " +
                    "every reach and a backfilled observation is withheld with BACKFILL_QUIET. A class that names " +
                    "FeedbackChannel.PROFILE is either building that producer — which must also widen the reach " +
                    "and update FeedbackAdmissionTest — or is about to deliver a campaign's findings to a channel " +
                    "the admission gate was never asked about. The exceptions are the two classes that derive the " +
                    "entitlement, and the enum that declares the constant"
            );
        rule.check(classes);
    }
}
