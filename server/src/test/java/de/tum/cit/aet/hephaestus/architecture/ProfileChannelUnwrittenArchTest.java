package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import org.junit.jupiter.api.Test;

/**
 * Makes "declared but unwritten" enforceable rather than merely commented.
 *
 * <p>{@code PracticeReviewTier.delivers(PROFILE)} returns {@code false} at every tier and
 * {@code ObservationOrigin.BACKFILL} is entitled to {@code PROFILE} and nothing else, so a backfilled
 * observation reaches no channel anybody can write to today. That is deliberate — posting on a merged pull
 * request notifies people about work they cannot act on, and the silence is recorded as
 * {@code FeedbackSuppressionReason.BACKFILL_QUIET} rather than swallowed.
 *
 * <p>The restriction only holds while nothing writes a {@code PROFILE} unit. {@code FeedbackAdmissionTest}
 * pins the entitlement matrix, but a matrix cannot see a {@code Feedback.builder().channel(PROFILE)} appearing
 * somewhere in {@code agent}: the ledger recorder would happily persist it, the tier gate would never have been
 * consulted, and a campaign's findings would start being delivered without anybody deciding they should be.
 * This rule closes that: naming the constant at all, outside the one place that derives the entitlement, is the
 * act that has to be deliberate.
 *
 * <p>Writing a profile surface is legitimate — it is the channel a backfill is <em>for</em>. When it is built,
 * add its producer to the allowance below, and expect {@code PracticeReviewTierTest} and
 * {@code FeedbackAdmissionTest} to fail on the same commit. Three failing pins is the intended cost of turning
 * a declared channel into a real one.
 */
class ProfileChannelUnwrittenArchTest extends HephaestusArchitectureTest {

    /**
     * The three allowed namers, and why each is not a producer:
     * <ul>
     *   <li>{@code FeedbackChannel} itself — an enum cannot declare a constant without its {@code <clinit>}
     *       and synthetic {@code $values()} touching it.</li>
     *   <li>{@code ObservationOrigin} — derives which channel a BACKFILL observation is entitled to.</li>
     *   <li>{@code PracticeReviewTier} — derives whether a tier delivers on the channel, and answers no.
     *       Matched by prefix because {@code javac} compiles its {@code switch} over the channel into a
     *       synthetic {@code PracticeReviewTier$1} holding the switch map, and that synthetic is where the
     *       field access actually lands.</li>
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
            .haveNameNotMatching(PracticeReviewTier.class.getName() + "(\\$.*)?")
            .should()
            .accessField(FeedbackChannel.class, "PROFILE")
            .because(
                "no producer writes a PROFILE feedback unit, so PracticeReviewTier.delivers(PROFILE) is false " +
                    "at every tier and a backfilled observation is withheld with BACKFILL_QUIET. A class that names " +
                    "FeedbackChannel.PROFILE is either building that producer — which must also raise the tier and " +
                    "update FeedbackAdmissionTest — or is about to deliver a campaign's findings to a channel the " +
                    "tier gate was never asked about. The exceptions are the two classes that derive the " +
                    "entitlement, and the enum that declares the constant"
            );
        rule.check(classes);
    }
}
