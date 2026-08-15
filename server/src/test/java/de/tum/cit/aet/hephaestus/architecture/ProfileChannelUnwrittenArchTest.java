package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import org.junit.jupiter.api.Test;

/**
 * Makes "declared but unwritten" enforceable rather than merely commented.
 *
 * <p>{@code ObservationOrigin.BACKFILL} is entitled to {@code PROFILE} and nothing else, so a backfilled
 * observation reaches no channel anybody can write to today — recorded as
 * {@code FeedbackSuppressionReason.BACKFILL_QUIET} rather than swallowed. {@code FeedbackAdmissionTest} pins
 * the entitlement matrix, but a matrix cannot see a new {@code Feedback.builder().channel(PROFILE)} call site
 * appearing elsewhere; this rule can, and it is the only thing that does — the admission gate asks about
 * autonomy and provenance, neither of which singles the channel out.
 */
class ProfileChannelUnwrittenArchTest extends HephaestusArchitectureTest {

    /**
     * The two allowed namers, and why neither is a producer:
     * <ul>
     *   <li>{@code FeedbackChannel} — an enum cannot declare a constant without its {@code <clinit>} and
     *       synthetic {@code $values()} touching it.</li>
     *   <li>{@code ObservationOrigin} — it derives the entitlement described above.</li>
     * </ul>
     */
    @Test
    void nothingButTheEntitlementDerivationNamesTheProfileChannel() {
        ArchRule rule = noClasses()
            .that()
            .haveNameNotMatching(FeedbackChannel.class.getName() + "(\\$.*)?")
            .and()
            .haveNameNotMatching(ObservationOrigin.class.getName() + "(\\$.*)?")
            .should()
            .accessField(FeedbackChannel.class, "PROFILE")
            .because(
                "no producer writes a PROFILE feedback unit, so a backfilled observation is withheld with " +
                    "BACKFILL_QUIET. A class that names FeedbackChannel.PROFILE is either building that producer " +
                    "— which must also update FeedbackAdmissionTest — or is about to deliver a campaign's " +
                    "findings to a channel the admission gate was never asked about. The exceptions are the " +
                    "class that derives the entitlement, and the enum that declares the constant"
            );
        rule.check(classes);
    }
}
