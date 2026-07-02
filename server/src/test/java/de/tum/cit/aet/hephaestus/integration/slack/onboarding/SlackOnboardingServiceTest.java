package de.tum.cit.aet.hephaestus.integration.slack.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.slack.api.model.block.LayoutBlock;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackWorkspaceResolver;
import de.tum.cit.aet.hephaestus.integration.slack.mentor.SlackMentorIdentityResolver;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * Slice 3 App Home onboarding CTA. Deterministic: the Slack round-trip is mocked, so these lock the routing
 * decisions (unknown team / already-linked / unlinked) and the deep-link the "Link Slack" button carries.
 */
class SlackOnboardingServiceTest extends BaseUnitTest {

    @Mock
    private SlackWorkspaceResolver workspaceResolver;

    @Mock
    private SlackMentorIdentityResolver identityResolver;

    @Mock
    private SlackMessageService messageService;

    private SlackOnboardingService service;

    @BeforeEach
    void setUp() {
        service = new SlackOnboardingService(
            workspaceResolver,
            identityResolver,
            messageService,
            "https://heph.example.com/"
        );
    }

    @Test
    void blankTeamOrUser_doesNothing() {
        service.onHomeOpened("", "U1");
        service.onHomeOpened("T1", "");

        verifyNoInteractions(workspaceResolver, identityResolver, messageService);
    }

    @Test
    void unknownTeam_postsNoCta() {
        when(workspaceResolver.resolveWorkspaceId("T1")).thenReturn(Optional.empty());

        service.onHomeOpened("T1", "U1");

        verify(messageService, never()).sendForWorkspace(anyLong(), any(), any(), any());
    }

    @Test
    void alreadyLinkedMember_postsNoCta() {
        when(workspaceResolver.resolveWorkspaceId("T1")).thenReturn(Optional.of(7L));
        when(identityResolver.resolveDeveloperLogin(7L, "T1", "U1")).thenReturn(Optional.of("octocat"));

        service.onHomeOpened("T1", "U1");

        verify(messageService, never()).sendForWorkspace(anyLong(), any(), any(), any());
    }

    @Test
    void unlinkedMember_dmsTheLinkCtaToTheOpeningUser() {
        when(workspaceResolver.resolveWorkspaceId("T1")).thenReturn(Optional.of(7L));
        when(identityResolver.resolveDeveloperLogin(7L, "T1", "U1")).thenReturn(Optional.empty());

        service.onHomeOpened("T1", "U1");

        // CTA is DM'd to the member (channel == their U… id), never to a shared channel.
        verify(messageService).sendForWorkspace(eq(7L), eq("U1"), any(), any());
    }

    @Test
    void linkUrl_isTheAuthenticatedLinkModeDeepLink() {
        assertThat(service.linkUrl()).isEqualTo(
            "https://heph.example.com/auth/login?provider=slack&mode=link&returnTo=/settings"
        );
    }

    @Test
    void linkCtaBlocks_carryTheDeepLinkButton() {
        List<LayoutBlock> blocks = service.linkCtaBlocks();

        assertThat(blocks).isNotEmpty();
        assertThat(blocks.toString()).contains("/auth/login?provider=slack&mode=link");
    }
}
