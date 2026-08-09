package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What one family of reviewable artifacts <em>is</em> — one bean per kind, contributed by the module
 * that owns the domain rather than by any vendor.
 *
 * <p>A kind belongs to the module that owns the entities behind it, never to a vendor that writes to
 * them. That placement is why a practice can name {@code scm.pull_request.merged} once and have it hold
 * for every provider of that domain, and why adding another provider changes no practice.
 *
 * <p>The descriptor is the <em>ceiling</em>: every signal the domain can ever have, every role it can
 * attribute, every lane feedback can land in. What a particular vendor delivers of that ceiling is
 * declared separately in {@link IntegrationManifest.ReviewContribution}, and the gap between the two is
 * the point — a vendor that raises only some of a kind's signals says so, instead of the rest simply
 * never firing.
 *
 * <p>Whether the kind can be reviewed on demand is <em>not</em> another method here: it is
 * {@link Signal#requestedByHand()} on one of {@link #signals()}. A kind that admits a hand-requested
 * review declares that signal; one that does not, declares none. Keeping it on the signal makes "named a
 * request signal the kind never declared" unrepresentable rather than merely validated, and keeps this
 * interface at the size the SPI focus rule allows.
 */
public interface ArtifactDescriptor {
    /** The family this descriptor defines. Unique across the application context. */
    ArtifactKind kind();

    /** Human-readable label for authoring and admin surfaces. */
    String displayName();

    /** Every signal this domain can raise, whatever the vendor. Must be non-empty. */
    List<Signal> signals();

    /**
     * The relations this artifact can identify a person in. Consent and delivery depend on it, so a
     * reviewable kind that names nobody has nowhere to send what it finds.
     */
    Set<ActorRole> roles();

    /**
     * The lanes feedback about this artifact can land in. A kind we do not review has none — which is a
     * real state, not an oversight: an artifact can be evidence without ever being a subject.
     */
    Set<FeedbackLane> lanes();

    /**
     * Whether a review can be run <em>about</em> this artifact, as opposed to it only supplying evidence
     * about something else. A reviewable kind must have a {@link ReviewContextBuilder}, because a review
     * with no way to assemble its subject is a job that can only fail.
     */
    boolean reviewable();

    /**
     * What a review of this kind can never settle, whatever its evidence.
     *
     * <p>Empty is legal only for a kind nothing reviews; a reviewable kind that names no limit fails the
     * review contract at startup, because a kind whose evidence settles everything is a claim nobody can
     * make and the silence would be inherited by every practice written against it.
     */
    default List<ReviewLimitation> reviewLimitations() {
        return List.of();
    }

    /** The declared signal with this name, if this descriptor declares it at all. */
    default Optional<Signal> signal(SignalName name) {
        return signals()
            .stream()
            .filter(signal -> signal.name().equals(name))
            .findFirst();
    }
}
