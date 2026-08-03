package de.tum.cit.aet.hephaestus.integration.scm.gitlab.workspace;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import de.tum.cit.aet.hephaestus.integration.core.egress.OutboundEgressGuard;
import de.tum.cit.aet.hephaestus.integration.core.egress.OutboundEgressSuppressedException;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class GitLabCommentReactionSinkTest extends BaseUnitTest {

    @Mock
    private GitLabGraphQlClientProvider provider;

    @Mock
    private OutboundEgressGuard egressGuard;

    @Test
    void shouldSkipReactionMutationWhenSilentModeIsEngaged() {
        doThrow(new OutboundEgressSuppressedException("test"))
            .when(egressGuard)
            .requireDeliveryAllowed("gitlab.react-to-comment");

        new GitLabCommentReactionSink(provider, egressGuard).react(1L, 42L, "eyes");

        verifyNoInteractions(provider);
    }
}
