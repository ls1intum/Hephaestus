package de.tum.cit.aet.hephaestus.integration.slack.onboarding;

import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.view.View;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountPreferencesQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountPreferencesQuery.PreferencesView;
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
 * App Home render. Deterministic: the {@code views.publish} round-trip is mocked, so these lock the view
 * assembly (disclosure + research-consent toggle reflecting current state + quiet-hours) and the linked/unlinked
 * routing (an unlinked member leads with the link CTA, no toggle).
 */
class SlackAppHomeServiceTest extends BaseUnitTest {

    @Mock
    private SlackWorkspaceResolver workspaceResolver;

    @Mock
    private SlackMentorIdentityResolver identityResolver;

    @Mock
    private AccountPreferencesQuery preferencesQuery;

    @Mock
    private SlackMessageService messageService;

    @Mock
    private SlackOnboardingService onboardingService;

    private SlackAppHomeService service;

    @BeforeEach
    void setUp() {
        service = new SlackAppHomeService(
            workspaceResolver,
            identityResolver,
            preferencesQuery,
            messageService,
            onboardingService
        );
    }

    @Test
    void linkedParticipatingMember_rendersDisclosureOptOutToggleAndQuietHours() {
        when(identityResolver.resolveDeveloperLogin(7L, "T1", "U1")).thenReturn(Optional.of("octocat"));
        when(preferencesQuery.preferencesForLogin("octocat")).thenReturn(Optional.of(new PreferencesView(true, true)));

        View view = service.buildHomeView(7L, "T1", "U1");

        assertThat(view.getType()).isEqualTo("home");
        String rendered = view.getBlocks().toString();
        assertThat(rendered).contains("Your privacy"); // disclosure
        assertThat(rendered).contains(SlackAppHomeService.ACTION_RESEARCH_OPT_OUT); // participating → offer opt-out
        assertThat(rendered).doesNotContain(SlackAppHomeService.ACTION_RESEARCH_OPT_IN);
        assertThat(rendered).contains("Quiet hours");
        assertThat(rendered).contains(SlackAppHomeService.ACTION_QUIET_HOURS);
    }

    @Test
    void linkedNonParticipatingMember_rendersOptInToggle() {
        when(identityResolver.resolveDeveloperLogin(7L, "T1", "U1")).thenReturn(Optional.of("octocat"));
        when(preferencesQuery.preferencesForLogin("octocat")).thenReturn(Optional.of(new PreferencesView(false, true)));

        View view = service.buildHomeView(7L, "T1", "U1");

        String rendered = view.getBlocks().toString();
        assertThat(rendered).contains(SlackAppHomeService.ACTION_RESEARCH_OPT_IN);
        assertThat(rendered).doesNotContain(SlackAppHomeService.ACTION_RESEARCH_OPT_OUT);
    }

    @Test
    void unlinkedMember_leadsWithLinkCta_andNoConsentToggle() {
        when(identityResolver.resolveDeveloperLogin(7L, "T1", "U1")).thenReturn(Optional.empty());
        List<LayoutBlock> cta = List.of(section(s -> s.text(markdownText("LINK_ME_MARKER"))));
        when(onboardingService.linkCtaBlocks()).thenReturn(cta);

        View view = service.buildHomeView(7L, "T1", "U1");

        String rendered = view.getBlocks().toString();
        assertThat(rendered).contains("LINK_ME_MARKER");
        assertThat(rendered).doesNotContain(SlackAppHomeService.ACTION_RESEARCH_OPT_OUT);
        assertThat(rendered).doesNotContain(SlackAppHomeService.ACTION_RESEARCH_OPT_IN);
        verifyNoInteractions(preferencesQuery); // no identity → no preference read
    }

    @Test
    void onHomeOpened_unknownTeam_doesNotPublish() {
        when(workspaceResolver.resolveWorkspaceId("T1")).thenReturn(Optional.empty());

        service.onHomeOpened("T1", "U1");

        verify(messageService, never()).publishHomeView(anyLong(), any(), any());
    }

    @Test
    void onHomeOpened_linkedMember_publishesHomeView() {
        when(workspaceResolver.resolveWorkspaceId("T1")).thenReturn(Optional.of(7L));
        when(identityResolver.resolveDeveloperLogin(7L, "T1", "U1")).thenReturn(Optional.of("octocat"));
        when(preferencesQuery.preferencesForLogin("octocat")).thenReturn(Optional.of(new PreferencesView(true, true)));

        service.onHomeOpened("T1", "U1");

        verify(messageService).publishHomeView(eq(7L), eq("U1"), any(View.class));
    }

    @Test
    void blankInput_doesNothing() {
        service.onHomeOpened("", "U1");
        service.onHomeOpened("T1", "");

        verifyNoInteractions(workspaceResolver, messageService);
    }
}
