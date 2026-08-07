package de.tum.cit.aet.hephaestus.integration.core.conformance;

import de.tum.cit.aet.hephaestus.integration.core.handler.IntegrationMessageHandler;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.RevisionScheme;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ActorRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactCatalog;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.core.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackLane;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationLifecycleListener;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ReviewContextBuilder;
import de.tum.cit.aet.hephaestus.integration.core.spi.Signal;
import de.tum.cit.aet.hephaestus.integration.core.spi.Stability;
import de.tum.cit.aet.hephaestus.integration.core.spi.SummaryChannel;
import io.nats.client.Message;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An integration that reviews an artifact Hephaestus has never heard of.
 *
 * <p>This is the standing proof that the review contract is real rather than a description of what
 * GitHub happens to do. {@code fixture.widget} exists nowhere in {@code src/main}: no entity, no table,
 * no enum constant, no branch. If the framework or the practices module ever grows a dependency on a
 * concrete artifact kind — a {@code switch} over pull requests, an {@code instanceof Issue}, a lane
 * assumed present — the tests built on this fixture stop compiling or stop passing. That failure is the
 * fixture's entire purpose; it is not a test of the widget.
 *
 * <p>Two deliberate compromises, both stated rather than hidden:
 * <ul>
 *   <li>It borrows an existing {@link IntegrationKind} instead of adding a fixture constant.
 *       {@code IntegrationKind} is persisted on connections and jobs, and a value that exists only for
 *       tests would be a value production data could acquire.
 *   <li>It cannot be driven through {@code PracticeReviewDetectionGate}, which still takes a
 *       {@code PullRequest} or an {@code Issue}. That coupling is frozen by
 *       {@code PracticesIntegrationBoundaryTest} and removed when bindings land; until then the
 *       practices-side proof runs through {@code PracticeSignalCoverage}, which is fully kind-agnostic.
 * </ul>
 */
final class FixtureIntegration {

    /** Borrowed, not invented — see the class javadoc. */
    static final IntegrationKind KIND = IntegrationKind.OUTLINE;

    static final ArtifactKind WIDGET = ArtifactKind.of("fixture.widget");

    static final SignalName WIDGET_ASSEMBLED = SignalName.of("fixture.widget.assembled");
    static final SignalName WIDGET_SHIPPED = SignalName.of("fixture.widget.shipped");

    static final EventTypeKey ASSEMBLY_EVENT = new EventTypeKey(KIND, "fixture.assembly");
    static final EventTypeKey SHIPMENT_EVENT = new EventTypeKey(KIND, "fixture.shipment");

    /** The stored trigger literal a practice binds to, mirroring how practices are still authored. */

    private FixtureIntegration() {}

    /**
     * Two signals with different revision schemes on purpose: one keyed on what a person wrote, one
     * terminal. A fixture where both behaved alike would not exercise the per-signal rule the ledger
     * turns on.
     */
    static ArtifactDescriptor descriptor() {
        return descriptor(true, Set.of(ActorRole.AUTHOR), Set.of(FeedbackLane.IN_CONTEXT_SUMMARY));
    }

    static ArtifactDescriptor descriptor(boolean reviewable, Set<ActorRole> roles, Set<FeedbackLane> lanes) {
        return descriptor(
            List.of(
                new Signal(
                    WIDGET_ASSEMBLED,
                    "Widget assembled",
                    Set.of(ASSEMBLY_EVENT),
                    RevisionScheme.CONTENT_DIGEST,
                    Stability.EXPERIMENTAL
                ),
                new Signal(
                    WIDGET_SHIPPED,
                    "Widget shipped",
                    Set.of(SHIPMENT_EVENT),
                    RevisionScheme.TERMINAL_STATE,
                    Stability.EXPERIMENTAL
                )
            ),
            reviewable,
            roles,
            lanes
        );
    }

    static ArtifactDescriptor descriptor(
        List<Signal> signals,
        boolean reviewable,
        Set<ActorRole> roles,
        Set<FeedbackLane> lanes
    ) {
        return new ArtifactDescriptor() {
            @Override
            public ArtifactKind kind() {
                return WIDGET;
            }

            @Override
            public String displayName() {
                return "Widget";
            }

            @Override
            public List<Signal> signals() {
                return signals;
            }

            @Override
            public Set<ActorRole> roles() {
                return roles;
            }

            @Override
            public Set<FeedbackLane> lanes() {
                return lanes;
            }

            @Override
            public boolean reviewable() {
                return reviewable;
            }
        };
    }

    static IntegrationManifest manifest() {
        return manifest(
            Set.of(Capability.FEEDBACK_DELIVERY),
            new IntegrationManifest.ReviewContribution(
                Set.of(WIDGET),
                Map.of(WIDGET, Set.of(WIDGET_ASSEMBLED, WIDGET_SHIPPED)),
                Map.of(WIDGET, Set.of(FeedbackLane.IN_CONTEXT_SUMMARY))
            )
        );
    }

    static IntegrationManifest manifest(
        Set<Capability> capabilities,
        IntegrationManifest.ReviewContribution contribution
    ) {
        return new IntegrationManifest() {
            @Override
            public IntegrationKind kind() {
                return KIND;
            }

            @Override
            public String displayName() {
                return "Fixture widget factory";
            }

            @Override
            public Set<Capability> declaredCapabilities() {
                return capabilities;
            }

            @Override
            public ReviewContribution reviewContribution() {
                return contribution;
            }
        };
    }

    static IntegrationMessageHandler handler(EventTypeKey key) {
        return new IntegrationMessageHandler() {
            @Override
            public EventTypeKey key() {
                return key;
            }

            @Override
            public void onMessage(Message msg) {
                // The registry only ever asks whether a key resolves; nothing here dispatches a message.
            }
        };
    }

    static ReviewContextBuilder contextBuilder() {
        return () -> WIDGET;
    }

    /** The catalog a practices-module surface sees when this fixture is the only registered domain. */
    static ArtifactCatalog artifactCatalog() {
        ArtifactDescriptor descriptor = descriptor();
        return new ArtifactCatalog() {
            @Override
            public java.util.Collection<ArtifactDescriptor> all() {
                return List.of(descriptor);
            }

            @Override
            public Optional<ArtifactDescriptor> descriptorFor(ArtifactKind kind) {
                return descriptor.kind().equals(kind) ? Optional.of(descriptor) : Optional.empty();
            }
        };
    }

    static ApiCredentialProvider credentialProvider() {
        return new ApiCredentialProvider() {
            @Override
            public IntegrationKind kind() {
                return KIND;
            }

            @Override
            public Optional<CredentialBundle> resolve(IntegrationRef ref) {
                return Optional.empty();
            }
        };
    }

    static IntegrationLifecycleListener lifecycleListener() {
        return () -> KIND;
    }

    /** The bean {@code FEEDBACK_DELIVERY} promises; removing it from a bootstrap must fail the boot. */
    static SummaryChannel feedbackChannel() {
        return new SummaryChannel() {
            @Override
            public IntegrationKind kind() {
                return KIND;
            }

            @Override
            public SummaryHandle postSummary(FeedbackTarget target, FeedbackContent content) {
                return new SummaryHandle("fixture-summary-1");
            }

            @Override
            public String formatPullRequestSubjectId(String repoFullName, int prNumber) {
                return repoFullName + "~" + prNumber;
            }
        };
    }
}
