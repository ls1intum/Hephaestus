package de.tum.cit.aet.hephaestus.integration.core.framework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationFamily;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

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
        manifestRegistry = new IntegrationManifestRegistry(
            List.of(
                stubManifest(
                    IntegrationKind.GITHUB,
                    Set.of(
                        Capability.WEBHOOK_INGEST,
                        Capability.FEEDBACK_DELIVERY,
                        Capability.INLINE_FINDINGS,
                        Capability.SCOPE_CHANGES,
                        Capability.APPROVAL_WORKFLOW
                    )
                ),
                stubManifest(
                    IntegrationKind.GITLAB,
                    Set.of(Capability.WEBHOOK_INGEST, Capability.FEEDBACK_DELIVERY, Capability.SCOPE_CHANGES)
                ),
                stubManifest(
                    IntegrationKind.SLACK,
                    Set.of(
                        Capability.WEBHOOK_INGEST,
                        Capability.URL_VERIFICATION_HANDSHAKE,
                        Capability.FEEDBACK_DELIVERY
                    )
                )
            )
        );
        resolver = new WorkspaceCapabilityResolver(connectionRepository, manifestRegistry);
    }

    // ── activeCapabilitiesFor ──────────────────────────────────────────────────

    @Nested
    class ActiveCapabilitiesFor {

        @Test
        void emptyWorkspaceReturnsEmpty() {
            when(
                connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE))
            ).thenReturn(List.of());

            assertThat(resolver.activeCapabilitiesFor(WORKSPACE_ID)).isEmpty();
        }

        @Test
        void singleGithubConnectionReturnsManifestCapabilities() {
            List<Connection> connections = List.of(connectionOf(IntegrationKind.GITHUB));
            when(
                connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE))
            ).thenReturn(connections);

            assertThat(resolver.activeCapabilitiesFor(WORKSPACE_ID)).containsExactlyInAnyOrder(
                Capability.WEBHOOK_INGEST,
                Capability.FEEDBACK_DELIVERY,
                Capability.INLINE_FINDINGS,
                Capability.SCOPE_CHANGES,
                Capability.APPROVAL_WORKFLOW
            );
        }

        @Test
        void multipleConnectionsReturnUnion() {
            List<Connection> connections = List.of(
                connectionOf(IntegrationKind.GITLAB),
                connectionOf(IntegrationKind.SLACK)
            );
            when(
                connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE))
            ).thenReturn(connections);

            assertThat(resolver.activeCapabilitiesFor(WORKSPACE_ID)).containsExactlyInAnyOrder(
                Capability.WEBHOOK_INGEST,
                Capability.FEEDBACK_DELIVERY,
                Capability.SCOPE_CHANGES,
                Capability.URL_VERIFICATION_HANDSHAKE
            );
        }

        @Test
        void unknownKindContributesNothing() {
            // OUTLINE has no stub manifest in this test setup → its capabilities are empty.
            List<Connection> connections = List.of(
                connectionOf(IntegrationKind.GITHUB),
                connectionOf(IntegrationKind.OUTLINE)
            );
            when(
                connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE))
            ).thenReturn(connections);

            assertThat(resolver.activeCapabilitiesFor(WORKSPACE_ID)).containsExactlyInAnyOrder(
                Capability.WEBHOOK_INGEST,
                Capability.FEEDBACK_DELIVERY,
                Capability.INLINE_FINDINGS,
                Capability.SCOPE_CHANGES,
                Capability.APPROVAL_WORKFLOW
            );
        }
    }

    // ── activeKindsFor ─────────────────────────────────────────────────────────

    @Nested
    class ActiveKindsFor {

        @Test
        void emptyWorkspaceReturnsEmpty() {
            when(
                connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE))
            ).thenReturn(List.of());

            assertThat(resolver.activeKindsFor(WORKSPACE_ID)).isEmpty();
        }

        @Test
        void returnsKindSet() {
            List<Connection> connections = List.of(
                connectionOf(IntegrationKind.GITHUB),
                connectionOf(IntegrationKind.SLACK)
            );
            when(
                connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE))
            ).thenReturn(connections);

            assertThat(resolver.activeKindsFor(WORKSPACE_ID)).containsExactlyInAnyOrder(
                IntegrationKind.GITHUB,
                IntegrationKind.SLACK
            );
        }
    }

    // ── isAvailable ────────────────────────────────────────────────────────────

    @Nested
    class IsAvailable {

        @Test
        void emptyWorkspaceRejectsConstrainedRequirement() {
            when(
                connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE))
            ).thenReturn(List.of());

            assertThat(
                resolver.isAvailable(WORKSPACE_ID, Set.of(Capability.WEBHOOK_INGEST), IntegrationFamily.SCM)
            ).isFalse();
        }

        @Test
        @DisplayName("empty requirement -> true regardless of workspace (no DB hit)")
        void universalRequirementShortCircuits() {
            // Repository is never consulted — universal requirement satisfied without a query.
            assertThat(resolver.isAvailable(WORKSPACE_ID, Set.of(), null)).isTrue();
        }

        @Test
        void nullRequirementSetTreatedAsEmpty() {
            assertThat(resolver.isAvailable(WORKSPACE_ID, null, null)).isTrue();
        }

        @Test
        void githubSatisfiesWebhookIngest() {
            List<Connection> connections = List.of(connectionOf(IntegrationKind.GITHUB));
            when(
                connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE))
            ).thenReturn(connections);

            assertThat(resolver.isAvailable(WORKSPACE_ID, Set.of(Capability.WEBHOOK_INGEST), null)).isTrue();
        }

        @Test
        void gitlabDoesNotSatisfyInlineFindings() {
            List<Connection> connections = List.of(connectionOf(IntegrationKind.GITLAB));
            when(
                connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE))
            ).thenReturn(connections);

            assertThat(resolver.isAvailable(WORKSPACE_ID, Set.of(Capability.INLINE_FINDINGS), null)).isFalse();
        }

        @Test
        void unionAcrossConnectionsSatisfiesInlineFindings() {
            List<Connection> connections = List.of(
                connectionOf(IntegrationKind.GITLAB),
                connectionOf(IntegrationKind.GITHUB)
            );
            when(
                connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE))
            ).thenReturn(connections);

            assertThat(resolver.isAvailable(WORKSPACE_ID, Set.of(Capability.INLINE_FINDINGS), null)).isTrue();
        }

        @Test
        void scmFamilyRequiredButOnlyMessagingActive() {
            List<Connection> connections = List.of(connectionOf(IntegrationKind.SLACK));
            when(
                connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE))
            ).thenReturn(connections);

            assertThat(
                resolver.isAvailable(WORKSPACE_ID, Set.of(Capability.WEBHOOK_INGEST), IntegrationFamily.SCM)
            ).isFalse();
        }

        @Test
        void messagingFamilySatisfied() {
            List<Connection> connections = List.of(connectionOf(IntegrationKind.SLACK));
            when(
                connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE))
            ).thenReturn(connections);

            assertThat(
                resolver.isAvailable(
                    WORKSPACE_ID,
                    Set.of(Capability.URL_VERIFICATION_HANDSHAKE),
                    IntegrationFamily.MESSAGING
                )
            ).isTrue();
        }

        @Test
        void familyOnlyGate() {
            List<Connection> connections = List.of(connectionOf(IntegrationKind.GITHUB));
            when(
                connectionRepository.findByWorkspaceIdAndState(eq(WORKSPACE_ID), eq(IntegrationState.ACTIVE))
            ).thenReturn(connections);

            assertThat(resolver.isAvailable(WORKSPACE_ID, Set.of(), IntegrationFamily.SCM)).isTrue();
            assertThat(resolver.isAvailable(WORKSPACE_ID, Set.of(), IntegrationFamily.KNOWLEDGE)).isFalse();
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static IntegrationManifest stubManifest(IntegrationKind kind, Set<Capability> capabilities) {
        return new IntegrationManifest() {
            @Override
            public IntegrationKind kind() {
                return kind;
            }

            @Override
            public String displayName() {
                return kind.name() + " (test)";
            }

            @Override
            public Set<Capability> declaredCapabilities() {
                return capabilities;
            }
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
