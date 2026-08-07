package de.tum.cit.aet.hephaestus.integration.core.conformance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.framework.ArtifactDescriptorRegistry;
import de.tum.cit.aet.hephaestus.integration.core.framework.DeclaredSignalCoverage;
import de.tum.cit.aet.hephaestus.integration.core.framework.IntegrationFrameworkBootstrap;
import de.tum.cit.aet.hephaestus.integration.core.framework.IntegrationManifestRegistry;
import de.tum.cit.aet.hephaestus.integration.core.framework.ReviewContractValidator;
import de.tum.cit.aet.hephaestus.integration.core.handler.IntegrationMessageHandlerRegistry;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTriggerOptions;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.review.DormantBinding;
import de.tum.cit.aet.hephaestus.practices.review.PracticeSignalCoverage;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * An artifact kind that exists nowhere in {@code src/main}, driven through the real machinery.
 *
 * <p>The claim under test is not that widgets work. It is that the integration framework and the
 * practices module contain no knowledge of which artifact kinds happen to exist — that
 * {@code scm.pull_request} is data to them, not a case. Every assertion here would still pass if pull
 * requests were deleted from the codebase, and every one of them fails the moment somebody reaches for a
 * concrete kind on either side of the contract.
 *
 * <p>What is deliberately absent: the trigger gate. {@code PracticeReviewDetectionGate} still takes a
 * {@code PullRequest} or an {@code Issue} directly, so a fixture kind cannot reach it. That coupling is
 * frozen and shrinking under {@code PracticesIntegrationBoundaryTest}; when it goes, the gate joins this
 * test rather than getting a fixture of its own.
 */
class FixtureIntegrationBootstrapTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 7L;

    @Test
    void theRealBootstrapAcceptsAnIntegrationForAKindThatDoesNotExist() {
        assertThatCode(bootstrap()::validate).doesNotThrowAnyException();
    }

    @Test
    void coverageIsComputedFromDeclarationsAlone() {
        DeclaredSignalCoverage coverage = coverage(false);

        assertThat(coverage.compiledCoverage())
            .as("both fixture signals are declared raised, so both are compiled-covered")
            .containsExactlyInAnyOrder(FixtureIntegration.WIDGET_ASSEMBLED, FixtureIntegration.WIDGET_SHIPPED);
        assertThat(coverage.raisedBy(FixtureIntegration.WIDGET_SHIPPED)).containsExactly(FixtureIntegration.KIND);
    }

    @Test
    void anUnconnectedWorkspaceCoversNothingWithoutFailing() {
        // The distinction the whole design turns on: nothing raises the signal here, and that is an
        // ordinary state of a workspace mid-onboarding — not a reason to refuse to serve it.
        DeclaredSignalCoverage coverage = coverage(false);

        assertThat(coverage.connectedCoverage(WORKSPACE_ID)).isEmpty();
        assertThat(coverage.compiledCoverage()).isNotEmpty();
    }

    @Test
    void aPracticeBoundToAnUncoveredSignalIsDormantWithAReason() {
        PracticeSignalCoverage practiceCoverage = practiceCoverage(false, practiceBoundTo("WidgetAssembled"));

        List<DormantBinding> dormant = practiceCoverage.dormantBindings(WORKSPACE_ID);

        assertThat(dormant).hasSize(1);
        assertThat(dormant.getFirst().signals()).containsExactly(FixtureIntegration.WIDGET_ASSEMBLED);
        assertThat(dormant.getFirst().raisedByAnyOf())
            .as("the reason names what to connect, so it is actionable rather than merely true")
            .containsExactly(FixtureIntegration.KIND);
        assertThat(dormant.getFirst().reason()).contains("connect one of");
    }

    @Test
    void connectingTheIntegrationEndsTheDormancy() {
        PracticeSignalCoverage practiceCoverage = practiceCoverage(true, practiceBoundTo("WidgetAssembled"));

        assertThat(practiceCoverage.dormantBindings(WORKSPACE_ID)).isEmpty();
    }

    @Test
    void aPracticeStaysLiveWhileAnyOneOfItsSignalsIsCovered() {
        // A practice watching two things is not dormant because one of them is unreachable; reporting it
        // as dormant would teach people that the dormancy report is noise.
        Practice practice = practiceBoundTo("WidgetAssembled", "NothingRaisesThis");
        PracticeSignalCoverage practiceCoverage = practiceCoverage(true, practice);

        assertThat(practiceCoverage.dormantBindings(WORKSPACE_ID)).isEmpty();
    }

    private IntegrationFrameworkBootstrap bootstrap() {
        return new IntegrationFrameworkBootstrap(
            new IntegrationManifestRegistry(List.of(FixtureIntegration.manifest())),
            List.of(),
            List.of(),
            List.of(),
            List.of(FixtureIntegration.credentialProvider()),
            List.of(),
            List.of(FixtureIntegration.feedbackChannel()),
            List.of(),
            List.of(),
            List.of(FixtureIntegration.lifecycleListener()),
            validator(),
            true
        );
    }

    private static ReviewContractValidator validator() {
        return new ReviewContractValidator(
            new ArtifactDescriptorRegistry(List.of(FixtureIntegration.descriptor())),
            new IntegrationMessageHandlerRegistry(
                List.of(
                    FixtureIntegration.handler(FixtureIntegration.ASSEMBLY_EVENT),
                    FixtureIntegration.handler(FixtureIntegration.SHIPMENT_EVENT)
                )
            ),
            List.of(FixtureIntegration.contextBuilder())
        );
    }

    private static DeclaredSignalCoverage coverage(boolean connected) {
        ConnectionService connections = mock(ConnectionService.class);
        // Lenient because half these tests only ask the compiled question, which never consults a
        // connection — that asymmetry is the point of the two coverages, not a stray stub.
        lenient()
            .when(connections.findActive(anyLong(), eq(FixtureIntegration.KIND)))
            .thenReturn(connected ? Optional.of(mock(Connection.class)) : Optional.empty());
        return new DeclaredSignalCoverage(
            new IntegrationManifestRegistry(List.of(FixtureIntegration.manifest())),
            connections
        );
    }

    private static PracticeSignalCoverage practiceCoverage(boolean connected, Practice... practices) {
        PracticeRepository repository = mock(PracticeRepository.class);
        when(repository.findByWorkspaceIdAndUsedInNewReviewsTrue(WORKSPACE_ID)).thenReturn(List.of(practices));
        return new PracticeSignalCoverage(
            coverage(connected),
            List.of(FixtureIntegration.vocabulary()),
            new PracticeTriggerOptions(FixtureIntegration.artifactCatalog(), List.of(FixtureIntegration.vocabulary())),
            repository
        );
    }

    private static Practice practiceBoundTo(String... triggerEvents) {
        Practice practice = new Practice();
        practice.setId(1L);
        practice.setSlug("assemble-widgets-carefully");
        var events = JsonNodeFactory.instance.arrayNode();
        for (String triggerEvent : triggerEvents) {
            events.add(triggerEvent);
        }
        practice.setTriggerEvents(events);
        return practice;
    }

    /** Guards the borrowed constant: if the fixture ever collides with a real kind, say so here. */
    @Test
    void borrowsAnIntegrationKindRatherThanInventingOne() {
        assertThat(FixtureIntegration.KIND).isIn((Object[]) IntegrationKind.values());
    }
}
