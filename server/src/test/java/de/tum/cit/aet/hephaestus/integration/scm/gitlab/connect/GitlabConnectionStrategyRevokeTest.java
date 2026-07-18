package de.tum.cit.aet.hephaestus.integration.scm.gitlab.connect;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.workspace.GitLabWebhookService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.ScmWorkspaceContentEraser;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Pins GitLab's F2 disconnect-erase wiring. Disconnect is GitLab's ONLY erase trigger (a PAT has no
 * vendor-side uninstall signal), so a regression here means GitLab-mirrored data becomes unerasable
 * short of manual SQL.
 *
 * <p>Order is load-bearing and asserted: the group webhook must be deregistered FIRST, while the PAT
 * is still live and the Connection still ACTIVE — that is the only window GitLab authorizes the hook
 * delete — and the local erase runs after.
 */
class GitlabConnectionStrategyRevokeTest extends BaseUnitTest {

    @Mock
    private GitLabWebhookService webhookService;

    @Mock
    private ScmWorkspaceContentEraser contentEraser;

    @InjectMocks
    private GitlabConnectionStrategy strategy;

    @Test
    void revoke_deregistersWebhookBeforeErasingTheScmMirror() {
        strategy.revoke(new IntegrationRef(IntegrationKind.GITLAB, 11L, "group-99"));

        InOrder order = inOrder(webhookService, contentEraser);
        order.verify(webhookService).deregisterActiveWebhook(11L);
        order.verify(contentEraser).eraseWorkspaceScmMirror(11L);
    }

    @Test
    void revoke_withNullRef_touchesNothing() {
        strategy.revoke(null);

        verifyNoInteractions(webhookService, contentEraser);
    }
}
