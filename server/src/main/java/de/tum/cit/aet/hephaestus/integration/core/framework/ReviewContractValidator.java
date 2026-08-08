package de.tum.cit.aet.hephaestus.integration.core.framework;

import de.tum.cit.aet.hephaestus.integration.core.handler.IntegrationMessageHandlerRegistry;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.core.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackLane;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest.ReviewContribution;
import de.tum.cit.aet.hephaestus.integration.core.spi.ReviewContextBuilder;
import de.tum.cit.aet.hephaestus.integration.core.spi.ReviewExecutionCatalog;
import de.tum.cit.aet.hephaestus.integration.core.spi.Signal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The enforcement half of the review contract. A declaration nobody checks is documentation, and
 * documentation is what let a complete set of ingested document events sit behind a manifest that never
 * claimed to raise anything.
 *
 * <p>Produces violation strings rather than throwing, so {@link IntegrationFrameworkBootstrap} can
 * report every problem in one message instead of one per restart.
 *
 * <p>The rules, and what each of them stops:
 * <ul>
 *   <li><b>Subset.</b> A raised signal must be declared by the kind's descriptor. Otherwise the
 *       descriptor stops being the definition of the domain and every vendor invents its own vocabulary.
 *   <li><b>Provenance.</b> A raised signal must name an ingested event of that same vendor, and that
 *       event must have a registered handler. This is the aspirational-signal check: without it a
 *       manifest can claim a signal nothing delivers, which reads as "configured and quiet" in every
 *       surface we have.
 *   <li><b>Reviewability.</b> A kind declared reviewable must have a {@link ReviewContextBuilder}, at
 *       least one role to attribute to, and at least one lane to speak on — a review with no subject,
 *       no addressee, or nowhere to land is a job that can only fail after we have paid for it.
 *   <li><b>Executability.</b> A kind declared reviewable must be one this build can actually <em>run</em> a
 *       review of, per {@link ReviewExecutionCatalog}. Every rule above checks a declaration against
 *       another declaration, and {@code docs.document} satisfied all of them for a whole slice while no
 *       job type, no handler and no submitter existed — so a workspace with Outline connected saw the
 *       bundled practice as live and it could never fire. That is the same shape of defect as a freshness
 *       value with no producer: a claim nothing can falsify. This is the rule that falsifies it.
 *   <li><b>Lanes.</b> A delivered lane must be one the descriptor allows and one the manifest holds the
 *       matching {@link Capability} for, so "GitLab posts inline notes" cannot outlive the bean that
 *       posts them.
 * </ul>
 */
@Component
public class ReviewContractValidator {

    /**
     * Which capability a vendor must own to claim a lane. {@link FeedbackLane#PROFILE} maps to no
     * capability because it is ours: the reflection surface lives inside Hephaestus, and an integration
     * claiming to deliver there is claiming a surface it cannot reach.
     */
    static final Map<FeedbackLane, Capability> LANE_CAPABILITIES = new EnumMap<>(
        Map.of(
            FeedbackLane.IN_CONTEXT_SUMMARY,
            Capability.FEEDBACK_DELIVERY,
            FeedbackLane.IN_CONTEXT_INLINE,
            Capability.INLINE_FINDINGS,
            FeedbackLane.CONVERSATION,
            Capability.FEEDBACK_DELIVERY
        )
    );

    /**
     * Lanes that exist but that no integration can reach, because the surface is ours.
     *
     * <p>Together with {@link #LANE_CAPABILITIES} this must classify every {@link FeedbackLane}: a lane
     * added to the enum with no rule here would be enforced only once some vendor happened to declare it,
     * which is to say discovered in production. The enum and both collections are compile-time constants,
     * so the answer is identical on every deployment and cannot change at runtime — which makes
     * {@code ReviewContractLaneRulesTest} the right place to assert it, where a failure costs a red build
     * rather than a crash-looping process.
     */
    static final Set<FeedbackLane> HEPHAESTUS_OWNED_LANES = EnumSet.of(FeedbackLane.PROFILE);

    private final ArtifactDescriptorRegistry descriptors;
    private final IntegrationMessageHandlerRegistry handlers;
    private final Map<ArtifactKind, List<ReviewContextBuilder>> contextBuilders;
    private final ReviewExecutionCatalog executionCatalog;

    public ReviewContractValidator(
        ArtifactDescriptorRegistry descriptors,
        IntegrationMessageHandlerRegistry handlers,
        List<ReviewContextBuilder> contextBuilders,
        ReviewExecutionCatalog executionCatalog
    ) {
        this.descriptors = descriptors;
        this.handlers = handlers;
        this.contextBuilders = contextBuilders
            .stream()
            .collect(Collectors.groupingBy(ReviewContextBuilder::artifactKind));
        // Required, not optional. An optional catalog would mean the executability rule quietly stops
        // applying in exactly the deployment where nothing runs reviews — which is the failure this rule
        // exists to make impossible, reintroduced one level up.
        this.executionCatalog = executionCatalog;
    }

    /** Rules about the domain's own declarations, independent of which vendors are enabled. */
    public List<String> validateDescriptors() {
        List<String> violations = new ArrayList<>();
        for (ArtifactDescriptor descriptor : descriptors.all()) {
            ArtifactKind kind = descriptor.kind();
            if (descriptor.signals().isEmpty()) {
                violations.add(kind + " declares no signals — an artifact nothing can happen to is not observable");
            }
            for (Signal signal : descriptor.signals()) {
                if (!kind.equals(signal.name().artifactKind())) {
                    violations.add(
                        kind + " declares signal " + signal.name() + " whose name belongs to another artifact kind"
                    );
                }
            }
            long distinctNames = descriptor.signals().stream().map(Signal::name).distinct().count();
            if (distinctNames != descriptor.signals().size()) {
                violations.add(kind + " declares the same signal name twice");
            }
            if (descriptor.reviewable()) {
                if (!contextBuilders.containsKey(kind)) {
                    violations.add(
                        kind + " is declared reviewable but no ReviewContextBuilder can assemble its review context"
                    );
                }
                // Assembling a context is not running a review. A builder proves the evidence can be
                // gathered; this proves something exists that would ever ask it to.
                if (!executionCatalog.executableKinds().contains(kind)) {
                    violations.add(
                        kind +
                            " is declared reviewable but no job type and handler can run a review of it — " +
                            "a kind that can be authored against and never executed is silence by declaration"
                    );
                }
                if (descriptor.roles().isEmpty()) {
                    violations.add(kind + " is declared reviewable but names nobody to attribute an observation to");
                }
                if (descriptor.lanes().isEmpty()) {
                    violations.add(kind + " is declared reviewable but declares no lane to deliver feedback on");
                }
                // Checked here rather than at catalog load: a new domain that declares itself reviewable
                // and names no limitation should fail against the descriptor that omitted it, not much
                // later with an error about practices.
                if (descriptor.reviewLimitations().isEmpty()) {
                    violations.add(
                        kind +
                            " is declared reviewable but names nothing its evidence cannot settle — " +
                            "a kind whose evidence answers every question is not a claim anyone can make"
                    );
                }
            } else if (!descriptor.lanes().isEmpty()) {
                violations.add(
                    kind + " is not reviewable yet declares feedback lanes " + descriptor.lanes() + " nothing can fill"
                );
            }
        }
        return violations;
    }

    /** Rules about what one enabled integration claims to contribute. */
    public List<String> validateContribution(IntegrationManifest manifest) {
        IntegrationKind kind = manifest.kind();
        ReviewContribution contribution = manifest.reviewContribution();
        List<String> violations = new ArrayList<>();

        for (ArtifactKind observed : contribution.observes()) {
            if (descriptors.descriptorFor(observed).isEmpty()) {
                violations.add(
                    kind +
                        " observes " +
                        observed +
                        " but no module contributes an ArtifactDescriptor for it — the kind is undefined"
                );
            }
        }

        for (Map.Entry<ArtifactKind, Set<SignalName>> entry : contribution.raises().entrySet()) {
            ArtifactKind raisedKind = entry.getKey();
            if (!contribution.observes().contains(raisedKind)) {
                violations.add(kind + " raises signals for " + raisedKind + " without observing it");
            }
            Optional<ArtifactDescriptor> descriptor = descriptors.descriptorFor(raisedKind);
            for (SignalName signalName : entry.getValue()) {
                if (!raisedKind.equals(signalName.artifactKind())) {
                    violations.add(
                        kind + " files signal " + signalName + " under the wrong artifact kind " + raisedKind
                    );
                    continue;
                }
                Optional<Signal> declared = descriptor.flatMap(d -> d.signal(signalName));
                if (declared.isEmpty()) {
                    violations.add(
                        kind +
                            " claims to raise " +
                            signalName +
                            " which " +
                            raisedKind +
                            "'s descriptor does not declare — raises must be a subset of the descriptor"
                    );
                    continue;
                }
                violations.addAll(validateProvenance(kind, declared.get()));
            }
        }

        violations.addAll(validateLanes(manifest, contribution));
        return violations;
    }

    private List<String> validateProvenance(IntegrationKind kind, Signal signal) {
        List<String> violations = new ArrayList<>();
        Set<EventTypeKey> own = signal
            .producedBy()
            .stream()
            .filter(key -> key.kind() == kind)
            .collect(Collectors.toUnmodifiableSet());
        if (own.isEmpty()) {
            violations.add(
                kind +
                    " claims to raise " +
                    signal.name() +
                    " but no ingested event of " +
                    kind +
                    " is declared to produce it — the signal could only ever be aspirational"
            );
            return violations;
        }
        for (EventTypeKey key : own) {
            if (handlers.resolve(key).isEmpty()) {
                violations.add(
                    kind +
                        " declares " +
                        signal.name() +
                        " is produced by event " +
                        key.eventType() +
                        " but no IntegrationMessageHandler is registered for it"
                );
            }
        }
        return violations;
    }

    private List<String> validateLanes(IntegrationManifest manifest, ReviewContribution contribution) {
        IntegrationKind kind = manifest.kind();
        List<String> violations = new ArrayList<>();
        for (Map.Entry<ArtifactKind, Set<FeedbackLane>> entry : contribution.delivers().entrySet()) {
            ArtifactKind deliveredKind = entry.getKey();
            if (!contribution.observes().contains(deliveredKind)) {
                violations.add(kind + " delivers feedback for " + deliveredKind + " without observing it");
            }
            Set<FeedbackLane> allowed = descriptors
                .descriptorFor(deliveredKind)
                .map(ArtifactDescriptor::lanes)
                .orElse(Set.of());
            for (FeedbackLane lane : entry.getValue()) {
                if (HEPHAESTUS_OWNED_LANES.contains(lane)) {
                    violations.add(
                        kind + " claims lane " + lane + " for " + deliveredKind + ", which no integration can deliver"
                    );
                    continue;
                }
                if (!allowed.contains(lane)) {
                    violations.add(
                        kind + " delivers " + lane + " for " + deliveredKind + ", a lane that artifact does not have"
                    );
                }
                Capability required = LANE_CAPABILITIES.get(lane);
                if (!manifest.declaredCapabilities().contains(required)) {
                    violations.add(
                        kind + " delivers " + lane + " for " + deliveredKind + " without declaring " + required
                    );
                }
            }
        }
        return violations;
    }
}
