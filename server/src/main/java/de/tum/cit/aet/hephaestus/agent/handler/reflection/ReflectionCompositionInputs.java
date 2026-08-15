package de.tum.cit.aet.hephaestus.agent.handler.reflection;

import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Stages the request that turns the feedback-composition stage on for one run, and the bounds it must
 * respect.
 *
 * <p>Absence is the off switch. The runner skips the stage when the file is not there, so deciding
 * <em>not</em> to compose costs nothing and needs no flag: a handler that says nothing gets a review and
 * no composition, which is what every review did before this existed.
 *
 * <p><b>Which runs compose.</b> Only a run whose measurements are of somebody's current work. A
 * {@link ObservationOrigin#BACKFILL} sweep measures a year of finished work in an afternoon; composing
 * from it would spend tokens on messages the router refuses anyway
 * ({@link ReflectionRoutingDecision#BACKFILL_HELD}), and if that refusal is ever lifted it should be lifted
 * deliberately rather than by a campaign nobody connected to the reflection surface.
 *
 * <p><b>Which artifact kinds compose.</b> Pull requests and issues, and deliberately not
 * {@code docs.document}. A document review's only declared lane is REFLECTION, so turning composition on
 * for it would ship the Outline subsystem's first feedback of any kind in the same change that ships the
 * lane — two untested things at once, on a surface whose whole point is that its text is private.
 * {@code DocumentReviewHandler} is one call to {@link #stage} away from it, once the lane has run for a
 * release on the kinds that already deliver.
 */
public final class ReflectionCompositionInputs {

    /**
     * Messages one run may compose, before the server's own per-recipient cap. Set here rather than only
     * in {@link ReflectionFeedbackPreparer} so the stage is not asked to write text that would be capped
     * away the moment it lands.
     */
    private static final int MAX_MESSAGES = ReflectionFeedbackPreparer.TOP_N_PER_RECIPIENT;

    private ReflectionCompositionInputs() {}

    /**
     * Add the composition request to a job's staged inputs, if this run should compose at all.
     *
     * @param origin which population this run's measurements belong to
     */
    public static void stage(Map<String, byte[]> files, ObservationOrigin origin) {
        if (origin == ObservationOrigin.BACKFILL) {
            return;
        }
        String request = """
            {
              "enabled": true,
              "maxMessages": %d,
              "minDistinctArtifacts": %d
            }
            """.formatted(MAX_MESSAGES, ReflectionFeedbackRouter.CORROBORATION_ARTIFACTS);
        files.put(SandboxLayout.FEEDBACK_COMPOSITION_PATH, request.getBytes(StandardCharsets.UTF_8));
    }
}
