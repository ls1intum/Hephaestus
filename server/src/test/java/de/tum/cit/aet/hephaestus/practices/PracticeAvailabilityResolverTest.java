package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.framework.WorkspaceCapabilityResolver;
import de.tum.cit.aet.hephaestus.integration.core.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationFamily;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * Verifies the practices-module shim forwards correctly to the integration-side
 * {@link WorkspaceCapabilityResolver}. The resolver itself is fully covered by its own
 * unit test — here we only assert the projection logic (capability strings -> Capability
 * enum, raw family pass-through) and that unknown strings are dropped before reaching the
 * downstream check.
 */
@DisplayName("PracticeAvailabilityResolver")
class PracticeAvailabilityResolverTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 11L;

    @Mock
    private WorkspaceCapabilityResolver capabilityResolver;

    private PracticeAvailabilityResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new PracticeAvailabilityResolver(capabilityResolver);
    }

    @Test
    @DisplayName("projects known capability strings to Capability enum")
    void forwardsKnownCapabilities() {
        Practice practice = practiceWith(
            new LinkedHashSet<>(List.of("INLINE_FINDINGS", "FEEDBACK_DELIVERY")),
            IntegrationFamily.SCM
        );
        when(
            capabilityResolver.isAvailable(
                eq(WORKSPACE_ID),
                eq(Set.of(Capability.INLINE_FINDINGS, Capability.FEEDBACK_DELIVERY)),
                eq(IntegrationFamily.SCM)
            )
        ).thenReturn(true);

        assertThat(resolver.isAvailable(WORKSPACE_ID, practice)).isTrue();
    }

    @Test
    @DisplayName("drops unknown capability strings before delegating")
    void dropsUnknownCapabilities() {
        Practice practice = practiceWith(new LinkedHashSet<>(List.of("INLINE_FINDINGS", "REMOVED_CAPABILITY")), null);
        when(
            capabilityResolver.isAvailable(eq(WORKSPACE_ID), eq(Set.of(Capability.INLINE_FINDINGS)), eq(null))
        ).thenReturn(true);

        assertThat(resolver.isAvailable(WORKSPACE_ID, practice)).isTrue();
    }

    @Test
    @DisplayName("propagates a false verdict from the underlying resolver")
    void propagatesFalseVerdict() {
        Practice practice = practiceWith(new LinkedHashSet<>(List.of("INLINE_FINDINGS")), null);
        when(capabilityResolver.isAvailable(eq(WORKSPACE_ID), any(), any())).thenReturn(false);

        assertThat(resolver.isAvailable(WORKSPACE_ID, practice)).isFalse();
    }

    @Test
    @DisplayName("universal practice still delegates (no short-circuit on this layer)")
    void universalPracticeDelegates() {
        Practice practice = practiceWith(new LinkedHashSet<>(), null);
        when(capabilityResolver.isAvailable(eq(WORKSPACE_ID), eq(Set.of()), eq(null))).thenReturn(true);

        assertThat(resolver.isAvailable(WORKSPACE_ID, practice)).isTrue();
        // The shim doesn't try to be cleverer than the resolver — every call goes through.
        verify(capabilityResolver, never()).activeCapabilitiesFor(WORKSPACE_ID);
    }

    private static Practice practiceWith(Set<String> requiredCapabilities, IntegrationFamily requiredFamily) {
        Practice practice = new Practice();
        practice.setRequiredCapabilities(new LinkedHashSet<>(requiredCapabilities));
        practice.setRequiredFamily(requiredFamily);
        return practice;
    }
}
