package de.tum.cit.aet.hephaestus.agent.handler.composition;

import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stages the request that turns the feedback-composition stage on for one run: which channels it may
 * write for, how many messages each may carry, and the bar a pattern claim has to clear.
 *
 * <p>Absence is the off switch. The runner skips the stage when the file is not there, so deciding
 * <em>not</em> to compose costs nothing and needs no flag: a handler that says nothing gets a review and
 * no composition, which is what every review did before this existed.
 *
 * <p><b>The channel set is carried, not inferred.</b> One composition turn writes for every enabled lane,
 * and which lanes are enabled is a property of the occasion rather than of the composer: an event review
 * has an artifact to anchor to and enables all three, while a scheduled workspace sweep has no diff and
 * no line to point at and must disable {@link FeedbackChannel#IN_CONTEXT} — posting a pattern claim on
 * whatever merge request happens to be open is a public message about a private habit. Reading the set
 * from here rather than from what the runner can see is what lets the same stage serve both.
 *
 * <p><b>Which runs compose.</b> Only a run whose measurements are of somebody's current work. A
 * {@link ObservationOrigin#BACKFILL} sweep measures a year of finished work in an afternoon; composing
 * from it would spend tokens on messages the routers refuse anyway, and if that refusal is ever lifted it
 * should be lifted deliberately rather than by a campaign nobody connected to these surfaces.
 *
 * <p><b>Which artifact kinds compose.</b> Pull requests and issues, and deliberately not
 * {@code docs.document}. A document review's only declared lane is IN_APP, so turning composition on
 * for it would ship the Outline subsystem's first feedback of any kind in the same change that ships the
 * lane — two untested things at once, on a surface whose whole point is that its text is private.
 */
public final class FeedbackCompositionInputs {

    public enum InContextPlacementKind {
        DIFF,
        ARTIFACT,
    }

    /**
     * Messages one run may compose per lane, before each lane's own per-recipient cap. Told to the stage
     * as well as enforced downstream, so it is not asked to write text that would be capped away the
     * moment it lands.
     *
     * <p>Each number restates a cap that a lane owns, rather than importing it. Composition is upstream
     * of every lane — it stages their input and parses their output — so a dependency the other way
     * would make the two packages mutually recursive, and one of them would stop being readable on its
     * own. {@code FeedbackCompositionCapsTest} asserts each number against the cap it restates, which is
     * what stops them drifting apart. Two of the three are not importable at all: the in-context cap is
     * package-private to the delivery package.
     */
    private static final Map<FeedbackChannel, Integer> MAX_UNITS = Map.of(
        FeedbackChannel.IN_CONTEXT,
        3,
        FeedbackChannel.IN_APP,
        2,
        FeedbackChannel.IN_CHAT,
        3
    );

    /**
     * Distinct pieces of work a problem must appear on before a message may call it a pattern. Restated
     * from the in-app router for the reason above; pinned by the same test.
     */
    private static final int MIN_DISTINCT_ARTIFACTS = 2;

    /** Every lane an event review can reach. A sweep passes a narrower set. */
    public static final Set<FeedbackChannel> EVENT_REVIEW_CHANNELS = EnumSet.allOf(FeedbackChannel.class);

    private FeedbackCompositionInputs() {}

    /**
     * Add the composition request to a job's staged inputs, if this run should compose at all.
     *
     * @param origin which population this run's measurements belong to
     */
    public static void stage(Map<String, byte[]> files, ObservationOrigin origin) {
        stage(files, origin, EVENT_REVIEW_CHANNELS, EnumSet.allOf(InContextPlacementKind.class));
    }

    /**
     * Add the composition request for a run that may only reach some of the lanes.
     *
     * @param channels the lanes this occasion may write for; an empty set is the same as not composing
     */
    public static void stage(Map<String, byte[]> files, ObservationOrigin origin, Set<FeedbackChannel> channels) {
        stage(files, origin, channels, EnumSet.allOf(InContextPlacementKind.class));
    }

    public static void stage(
        Map<String, byte[]> files,
        ObservationOrigin origin,
        Set<FeedbackChannel> channels,
        Set<InContextPlacementKind> inContextPlacements
    ) {
        if (origin == ObservationOrigin.BACKFILL || channels.isEmpty()) {
            return;
        }
        if (channels.contains(FeedbackChannel.IN_CONTEXT) && inContextPlacements.isEmpty()) {
            throw new IllegalArgumentException("IN_CONTEXT requires at least one placement kind");
        }
        String request = """
            {
              "enabled": true,
              "minDistinctArtifacts": %d,
              "inContextPlacementKinds": [%s],
              "channels": {
            %s
              }
            }
            """.formatted(
                MIN_DISTINCT_ARTIFACTS,
                inContextPlacements
                    .stream()
                    .map(kind -> '"' + kind.name() + '"')
                    .collect(Collectors.joining(", ")),
                channelBounds(channels)
            );
        files.put(SandboxLayout.FEEDBACK_COMPOSITION_PATH, request.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Every lane is named, enabled or not. A disabled lane present with {@code "enabled": false} tells the
     * composer the surface exists and is closed for this run; a lane simply missing would read as a lane
     * the system does not have, and the per-channel contract is only legible in contrast — "the note on
     * the work is written elsewhere this time" is unstatable if the note is not mentioned.
     */
    private static String channelBounds(Set<FeedbackChannel> enabled) {
        return EnumSet.allOf(FeedbackChannel.class)
            .stream()
            .map(channel ->
                "    \"%s\": { \"enabled\": %s, \"maxUnits\": %d }".formatted(
                    channel.name(),
                    enabled.contains(channel),
                    MAX_UNITS.getOrDefault(channel, 1)
                )
            )
            .collect(Collectors.joining(",\n"));
    }
}
