package de.tum.cit.aet.hephaestus.integration.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.registry.Connection;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationFamily;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@DisplayName("WorkspaceCapabilityResolver")
class WorkspaceCapabilityResolverTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 7L;

    @Mock
    private ConnectionRepository connectionRepository;

    private IntegrationManifestRegistry manifestRegistry;
    private WorkspaceCapabilityResolver resolver;

    @BeforeEach
    void setUp() {
        // Build a real registry from in-memory manifests so the assertions exercise the
        // actual lookup path (capabilitiesFor returns {} for unregistered kinds).
        manifestRegistry = new IntegrationManifestRegistry(List.of(
            stubManifest(IntegrationKind.GITHUB, Set.of(
                Capability.WEBHOOK_INGEST,
                Capability.FEEDBACK_DELIVERY,
                Capability.INLINE_FINDINGS,
                Capability.GIT_CONTENT_ACCESS,
                Capability.BACKFILL_SYNC
            )),
            stubManifest(IntegrationKind.GITLAB, Set.of(
                Capability.WEBHOOK_INGEST,
                Capability.FEEDBACK_DELIVERY,
                Capability.GIT_CONTENT_ACCESS
            )),
            stubManifest(IntegrationKind.SLACK, Set.of(
                Capability.WEBHOOK_INGEST,
                Capability.URL_VERIFICATION_HANDSHAKE,
                Capability.FEEDBACK_DELIVERY
            ))
        ));
        resolver = new WorkspaceCapabilityResolver(connectionRepository, manifestRegistry);
    }

    // ── activeCapabilitiesFor ──────────────────────────────────────────────────

    @Nested
    @DisplayName("activeCapabilitiesFor")
    class ActiveCapabilitiesFor {

        @Test
        @DisplayName("empty workspace -> empty set")
        void emptyWorkspaceReturnsEmpty() {
            when(connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE)))
                .thenReturn(List.of());

            assertThat(resolver.activeCapabilitiesFor(WORKSPACE_ID)).isEmpty();
        }

        @Test
        @DisplayName("single GitHub ACTIVE -> manifest capabilities")
        void singleGithubConnectionReturnsManifestCapabilities() {
            List<Connection> connections = List.of(connectionOf(IntegrationKind.GITHUB));
            when(connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE)))
                .thenReturn(connections);

            assertThat(resolver.activeCapabilitiesFor(WORKSPACE_ID))
                .containsExactlyInAnyOrder(
                    Capability.WEBHOOK_INGEST,
                    Capability.FEEDBACK_DELIVERY,
                    Capability.INLINE_FINDINGS,
                    Capability.GIT_CONTENT_ACCESS,
                    Capability.BACKFILL_SYNC
                );
        }

        @Test
        @DisplayName("multiple ACTIVE -> union of manifests")
        void multipleConnectionsReturnUnion() {
            List<Connection> connections = List.of(
                connectionOf(IntegrationKind.GITLAB),
                connectionOf(IntegrationKind.SLACK)
            );
            when(connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE)))
                .thenReturn(connections);

            assertThat(resolver.activeCapabilitiesFor(WORKSPACE_ID))
                .containsExactlyInAnyOrder(
                    Capability.WEBHOOK_INGEST,
                    Capability.FEEDBACK_DELIVERY,
                    Capability.GIT_CONTENT_ACCESS,
                    Capability.URL_VERIFICATION_HANDSHAKE
                );
        }

        @Test
        @DisplayName("unknown kind contributes nothing")
        void unknownKindContributesNothing() {
            // OUTLINE has no stub manifest in this test setup → its capabilities are empty.
            List<Connection> connections = List.of(
                connectionOf(IntegrationKind.GITHUB),
                connectionOf(IntegrationKind.OUTLINE)
            );
            when(connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE)))
                .thenReturn(connections);

            assertThat(resolver.activeCapabilitiesFor(WORKSPACE_ID))
                .containsExactlyInAnyOrder(
                    Capability.WEBHOOK_INGEST,
                    Capability.FEEDBACK_DELIVERY,
                    Capability.INLINE_FINDINGS,
                    Capability.GIT_CONTENT_ACCESS,
                    Capability.BACKFILL_SYNC
                );
        }
    }

    // ── activeKindsFor ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("activeKindsFor")
    class ActiveKindsFor {

        @Test
        void emptyWorkspaceReturnsEmpty() {
            when(connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE)))
                .thenReturn(List.of());

            assertThat(resolver.activeKindsFor(WORKSPACE_ID)).isEmpty();
        }

        @Test
        void returnsKindSet() {
            List<Connection> connections = List.of(
                connectionOf(IntegrationKind.GITHUB),
                connectionOf(IntegrationKind.SLACK)
            );
            when(connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE)))
                .thenReturn(connections);

            assertThat(resolver.activeKindsFor(WORKSPACE_ID))
                .containsExactlyInAnyOrder(IntegrationKind.GITHUB, IntegrationKind.SLACK);
        }
    }

    // ── isAvailable ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isAvailable")
    class IsAvailable {

        @Test
        @DisplayName("empty workspace + non-empty requirement -> false")
        void emptyWorkspaceRejectsConstrainedRequirement() {
            when(connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE)))
                .thenReturn(List.of());

            assertThat(resolver.isAvailable(
                WORKSPACE_ID, Set.of(Capability.WEBHOOK_INGEST), IntegrationFamily.SCM
            )).isFalse();
        }

        @Test
        @DisplayName("empty requirement -> true regardless of workspace (no DB hit)")
        void universalRequirementShortCircuits() {
            // Repository is never consulted — universal requirement satisfied without a query.
            assertThat(resolver.isAvailable(WORKSPACE_ID, Set.of(), null)).isTrue();
        }

        @Test
        @DisplayName("null requirement set is treated as empty")
        void nullRequirementSetTreatedAsEmpty() {
            assertThat(resolver.isAvailable(WORKSPACE_ID, null, null)).isTrue();
        }

        @Test
        @DisplayName("GitHub ACTIVE + required={WEBHOOK_INGEST} -> true")
        void githubSatisfiesWebhookIngest() {
            List<Connection> connections = List.of(connectionOf(IntegrationKind.GITHUB));
            when(connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE)))
                .thenReturn(connections);

            assertThat(resolver.isAvailable(WORKSPACE_ID, Set.of(Capability.WEBHOOK_INGEST), null)).isTrue();
        }

        @Test
        @DisplayName("GitLab ACTIVE + required={INLINE_FINDINGS} -> false (not declared)")
        void gitlabDoesNotSatisfyInlineFindings() {
            List<Connection> connections = List.of(connectionOf(IntegrationKind.GITLAB));
            when(connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE)))
                .thenReturn(connections);

            assertThat(resolver.isAvailable(WORKSPACE_ID, Set.of(Capability.INLINE_FINDINGS), null)).isFalse();
        }

        @Test
        @DisplayName("GitLab + GitHub ACTIVE + required={INLINE_FINDINGS} -> true via union")
        void unionAcrossConnectionsSatisfiesInlineFindings() {
            List<Connection> connections = List.of(
                connectionOf(IntegrationKind.GITLAB),
                connectionOf(IntegrationKind.GITHUB)
            );
            when(connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE)))
                .thenReturn(connections);

            assertThat(resolver.isAvailable(WORKSPACE_ID, Set.of(Capability.INLINE_FINDINGS), null)).isTrue();
        }

        @Test
        @DisplayName("requiredFamily=SCM + only Slack ACTIVE -> false")
        void scmFamilyRequiredButOnlyMessagingActive() {
            List<Connection> connections = List.of(connectionOf(IntegrationKind.SLACK));
            when(connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE)))
                .thenReturn(connections);

            assertThat(resolver.isAvailable(
                WORKSPACE_ID, Set.of(Capability.WEBHOOK_INGEST), IntegrationFamily.SCM
            )).isFalse();
        }

        @Test
        @DisplayName("requiredFamily=MESSAGING + Slack ACTIVE + capability satisfied -> true")
        void messagingFamilySatisfied() {
            List<Connection> connections = List.of(connectionOf(IntegrationKind.SLACK));
            when(connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE)))
                .thenReturn(connections);

            assertThat(resolver.isAvailable(
                WORKSPACE_ID, Set.of(Capability.URL_VERIFICATION_HANDSHAKE), IntegrationFamily.MESSAGING
            )).isTrue();
        }

        @Test
        @DisplayName("requiredFamily without capabilities -> family-only gate")
        void familyOnlyGate() {
            List<Connection> connections = List.of(connectionOf(IntegrationKind.GITHUB));
            when(connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE)))
                .thenReturn(connections);

            assertThat(resolver.isAvailable(WORKSPACE_ID, Set.of(), IntegrationFamily.SCM)).isTrue();
            assertThat(resolver.isAvailable(WORKSPACE_ID, Set.of(), IntegrationFamily.KNOWLEDGE)).isFalse();
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static IntegrationManifest stubManifest(IntegrationKind kind, Set<Capability> capabilities) {
        return new IntegrationManifest() {
            @Override public IntegrationKind kind() { return kind; }
            @Override public String displayName() { return kind.name() + " (test)"; }
            @Override public Set<Capability> declaredCapabilities() { return capabilities; }
        };
    }

    /**
     * Build the Connection mock and its {@code getKind()} stub. Callers must finish
     * constructing the {@code List<Connection>} BEFORE opening any outer
     * {@code when().thenReturn(...)} — Mockito's strict-stubbing mode flags inline mock
     * construction inside the {@code thenReturn(...)} argument as "unfinished stubbing".
     */
    private Connection connectionOf(IntegrationKind kind) {
        Connection connection = org.mockito.Mockito.mock(Connection.class);
        when(connection.getKind()).thenReturn(kind);
        return connection;
    }
}
