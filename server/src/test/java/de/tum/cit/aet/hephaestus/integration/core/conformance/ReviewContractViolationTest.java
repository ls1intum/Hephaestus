package de.tum.cit.aet.hephaestus.integration.core.conformance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.core.framework.ArtifactDescriptorRegistry;
import de.tum.cit.aet.hephaestus.integration.core.framework.IntegrationFrameworkBootstrap;
import de.tum.cit.aet.hephaestus.integration.core.framework.IntegrationManifestRegistry;
import de.tum.cit.aet.hephaestus.integration.core.framework.ReviewContractValidator;
import de.tum.cit.aet.hephaestus.integration.core.handler.IntegrationMessageHandler;
import de.tum.cit.aet.hephaestus.integration.core.handler.IntegrationMessageHandlerRegistry;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.RevisionScheme;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ActorRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.core.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackLane;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest.ReviewContribution;
import de.tum.cit.aet.hephaestus.integration.core.spi.ReviewContextBuilder;
import de.tum.cit.aet.hephaestus.integration.core.spi.Signal;
import de.tum.cit.aet.hephaestus.integration.core.spi.Stability;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Deliberately broken integrations, one per rule, each asserted by the words it fails with.
 *
 * <p>A validator nobody has watched fail is a validator that might not run. These fixtures also pin the
 * <em>messages</em>, not just the throw: an operator reading a refused boot has to be told which
 * declaration is wrong and what would fix it, and a message that degrades to "validation failed" is a
 * regression even though every assertion on the exception type would still pass.
 */
class ReviewContractViolationTest extends BaseUnitTest {

    private static final ArtifactKind WIDGET = FixtureIntegration.WIDGET;
    private static final SignalName UNDECLARED = SignalName.of("fixture.widget.recalled");
    private static final SignalName ASSEMBLED = FixtureIntegration.WIDGET_ASSEMBLED;

    @Nested
    class SignalsMustBeReal {

        @Test
        void raisingASignalTheDescriptorDoesNotDeclareIsRefused() {
            List<String> violations = validate(
                contribution(Set.of(WIDGET), Map.of(WIDGET, Set.of(UNDECLARED)), Map.of())
            );

            assertThat(violations).singleElement().asString().contains("raises must be a subset of the descriptor");
        }

        @Test
        void claimingASignalNoEventOfYoursProducesIsRefused() {
            // The aspirational-signal check. The descriptor declares the signal, but only some other
            // vendor's event raises it, so this integration claiming it would be a promise it cannot keep.
            ArtifactDescriptor foreign = FixtureIntegration.descriptor(
                List.of(
                    new Signal(
                        ASSEMBLED,
                        "Widget assembled",
                        Set.of(new EventTypeKey(IntegrationKind.GITHUB, "repository.pull_request")),
                        RevisionScheme.CONTENT_DIGEST,
                        Stability.EXPERIMENTAL
                    )
                ),
                true,
                Set.of(ActorRole.AUTHOR),
                Set.of(FeedbackLane.IN_CONTEXT_SUMMARY)
            );

            List<String> violations = validate(
                foreign,
                contribution(Set.of(WIDGET), Map.of(WIDGET, Set.of(ASSEMBLED)), Map.of())
            );

            assertThat(violations).singleElement().asString().contains("could only ever be aspirational");
        }

        @Test
        void declaringProvenanceWithNoRegisteredHandlerIsRefused() {
            ReviewContractValidator validator = new ReviewContractValidator(
                new ArtifactDescriptorRegistry(List.of(FixtureIntegration.descriptor())),
                new IntegrationMessageHandlerRegistry(List.of()),
                List.of(FixtureIntegration.contextBuilder()),
                FixtureIntegration.executionCatalog()
            );

            List<String> violations = validator.validateContribution(
                FixtureIntegration.manifest(
                    Set.of(),
                    contribution(Set.of(WIDGET), Map.of(WIDGET, Set.of(ASSEMBLED)), Map.of())
                )
            );

            assertThat(violations)
                .singleElement()
                .asString()
                .contains("no IntegrationMessageHandler is registered for it");
        }

        @Test
        void raisingForAKindYouDoNotObserveIsRefused() {
            List<String> violations = validate(contribution(Set.of(), Map.of(WIDGET, Set.of(ASSEMBLED)), Map.of()));

            assertThat(violations).anyMatch(v -> v.contains("without observing it"));
        }

        @Test
        void observingAKindNoModuleDefinesIsRefused() {
            ReviewContractValidator validator = new ReviewContractValidator(
                new ArtifactDescriptorRegistry(List.of()),
                new IntegrationMessageHandlerRegistry(List.of()),
                List.of(),
                FixtureIntegration.executionCatalog()
            );

            List<String> violations = validator.validateContribution(
                FixtureIntegration.manifest(Set.of(), contribution(Set.of(WIDGET), Map.of(), Map.of()))
            );

            assertThat(violations).singleElement().asString().contains("the kind is undefined");
        }
    }

    @Nested
    class ReviewableKindsMustBeServiceable {

        @Test
        void aReviewableKindWithNoContextBuilderIsRefused() {
            ReviewContractValidator validator = new ReviewContractValidator(
                new ArtifactDescriptorRegistry(List.of(FixtureIntegration.descriptor())),
                new IntegrationMessageHandlerRegistry(List.of()),
                List.of(),
                FixtureIntegration.executionCatalog()
            );

            assertThat(validator.validateDescriptors())
                .singleElement()
                .asString()
                .contains("no ReviewContextBuilder can assemble its review context");
        }

        @Test
        void aReviewableKindThatCanNameNobodyIsRefused() {
            assertThat(
                validateDescriptors(
                    FixtureIntegration.descriptor(true, Set.of(), Set.of(FeedbackLane.IN_CONTEXT_SUMMARY))
                )
            )
                .singleElement()
                .asString()
                .contains("names nobody to attribute an observation to");
        }

        @Test
        void aReviewableKindWithNowhereToSpeakIsRefused() {
            assertThat(validateDescriptors(FixtureIntegration.descriptor(true, Set.of(ActorRole.AUTHOR), Set.of())))
                .singleElement()
                .asString()
                .contains("declares no lane to deliver feedback on");
        }

        @Test
        void anUnreviewableKindWithLanesIsRefused() {
            // A content source is a legitimate thing to be; a content source with a feedback lane is a
            // declaration nothing would ever act on.
            assertThat(
                validateDescriptors(
                    FixtureIntegration.descriptor(
                        false,
                        Set.of(ActorRole.AUTHOR),
                        Set.of(FeedbackLane.IN_CONTEXT_SUMMARY)
                    )
                )
            )
                .singleElement()
                .asString()
                .contains("declares feedback lanes");
        }

        @Test
        @DisplayName("a reviewable kind nothing can run a review of is refused")
        void aReviewableKindWithNoWayToExecuteIsRefused() {
            // The rule that would have caught docs.document. Every other check here passed for it: a
            // descriptor, a context builder, a role, a lane, its limitations — and no job type, no
            // handler, no submitter, so the practice bound to it read as live and fired never.
            ReviewContractValidator validator = new ReviewContractValidator(
                new ArtifactDescriptorRegistry(List.of(FixtureIntegration.descriptor())),
                new IntegrationMessageHandlerRegistry(List.of()),
                List.of(FixtureIntegration.contextBuilder()),
                FixtureIntegration.noExecutionCatalog()
            );

            assertThat(validator.validateDescriptors())
                .singleElement()
                .asString()
                .contains("no job type and handler can run a review of it");
        }

        @Test
        @DisplayName("a kind that is not reviewable needs no way to run one")
        void anUnreviewableKindIsNotAskedToBeExecutable() {
            ReviewContractValidator validator = new ReviewContractValidator(
                new ArtifactDescriptorRegistry(List.of(FixtureIntegration.descriptor(false, Set.of(), Set.of()))),
                new IntegrationMessageHandlerRegistry(List.of()),
                List.of(),
                FixtureIntegration.noExecutionCatalog()
            );

            assertThat(validator.validateDescriptors()).isEmpty();
        }

        @Test
        void twoDescriptorsForOneKindIsFatal() {
            assertThatThrownBy(() ->
                new ArtifactDescriptorRegistry(
                    List.of(FixtureIntegration.descriptor(), FixtureIntegration.descriptor())
                )
            )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate ArtifactDescriptor for kind=fixture.widget");
        }
    }

    @Nested
    class LanesMustBeBacked {

        @Test
        void deliveringALaneTheArtifactDoesNotHaveIsRefused() {
            List<String> violations = validate(
                contribution(Set.of(WIDGET), Map.of(), Map.of(WIDGET, Set.of(FeedbackLane.IN_CONTEXT_INLINE))),
                Set.of(Capability.INLINE_FINDINGS)
            );

            assertThat(violations).singleElement().asString().contains("a lane that artifact does not have");
        }

        @Test
        void deliveringALaneWithoutItsCapabilityIsRefused() {
            List<String> violations = validate(
                contribution(Set.of(WIDGET), Map.of(), Map.of(WIDGET, Set.of(FeedbackLane.IN_CONTEXT_SUMMARY))),
                Set.of()
            );

            assertThat(violations).singleElement().asString().contains("without declaring FEEDBACK_DELIVERY");
        }

        @Test
        void claimingHephaestusOwnProfileSurfaceIsRefused() {
            List<String> violations = validate(
                contribution(Set.of(WIDGET), Map.of(), Map.of(WIDGET, Set.of(FeedbackLane.PROFILE))),
                Set.of(Capability.FEEDBACK_DELIVERY)
            );

            assertThat(violations).singleElement().asString().contains("which no integration can deliver");
        }
    }

    @Test
    void theBootstrapRefusesToStartAndNamesTheOffendingDeclaration() {
        // End to end: the review contract is enforced by the same fail-fast the capability checks use, so a
        // bad contribution stops a boot rather than being reported somewhere nobody reads.
        IntegrationFrameworkBootstrap bootstrap = new IntegrationFrameworkBootstrap(
            new IntegrationManifestRegistry(
                List.of(
                    FixtureIntegration.manifest(
                        Set.of(),
                        contribution(Set.of(WIDGET), Map.of(WIDGET, Set.of(UNDECLARED)), Map.of())
                    )
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(FixtureIntegration.credentialProvider()),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(FixtureIntegration.lifecycleListener()),
            validator(FixtureIntegration.descriptor()),
            true
        );

        assertThatThrownBy(bootstrap::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("fixture.widget.recalled")
            .hasMessageContaining("raises must be a subset of the descriptor");
    }

    private static ReviewContribution contribution(
        Set<ArtifactKind> observes,
        Map<ArtifactKind, Set<SignalName>> raises,
        Map<ArtifactKind, Set<FeedbackLane>> delivers
    ) {
        return new ReviewContribution(observes, raises, delivers);
    }

    private static List<String> validate(ReviewContribution contribution) {
        return validate(FixtureIntegration.descriptor(), contribution, Set.of());
    }

    private static List<String> validate(ReviewContribution contribution, Set<Capability> capabilities) {
        return validate(FixtureIntegration.descriptor(), contribution, capabilities);
    }

    private static List<String> validate(ArtifactDescriptor descriptor, ReviewContribution contribution) {
        return validate(descriptor, contribution, Set.of());
    }

    private static List<String> validate(
        ArtifactDescriptor descriptor,
        ReviewContribution contribution,
        Set<Capability> capabilities
    ) {
        return validator(descriptor).validateContribution(FixtureIntegration.manifest(capabilities, contribution));
    }

    private static List<String> validateDescriptors(ArtifactDescriptor descriptor) {
        return validator(descriptor).validateDescriptors();
    }

    private static ReviewContractValidator validator(ArtifactDescriptor descriptor) {
        List<IntegrationMessageHandler> handlers = descriptor
            .signals()
            .stream()
            .flatMap(signal -> signal.producedBy().stream())
            .distinct()
            .map(FixtureIntegration::handler)
            .toList();
        List<ReviewContextBuilder> builders = descriptor.reviewable()
            ? List.of(FixtureIntegration.contextBuilder())
            : List.of();
        return new ReviewContractValidator(
            new ArtifactDescriptorRegistry(List.of(descriptor)),
            new IntegrationMessageHandlerRegistry(handlers),
            builders,
            FixtureIntegration.executionCatalog()
        );
    }
}
