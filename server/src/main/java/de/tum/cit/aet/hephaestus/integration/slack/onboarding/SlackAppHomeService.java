package de.tum.cit.aet.hephaestus.integration.slack.onboarding;

import static com.slack.api.model.block.Blocks.actions;
import static com.slack.api.model.block.Blocks.divider;
import static com.slack.api.model.block.Blocks.header;
import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.asElements;
import static com.slack.api.model.block.element.BlockElements.button;

import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.view.View;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountPreferencesQuery;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackWorkspaceResolver;
import de.tum.cit.aet.hephaestus.integration.slack.mentor.SlackMentorIdentityResolver;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackSendException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Slack App Home renderer (Slice 4): on {@code app_home_opened}, publish the persistent Home tab via
 * {@code views.publish}. The Home tab carries three things the DM CTA (Slice 3) cannot:
 *
 * <ul>
 *   <li>a <strong>privacy disclosure</strong> block — what the mentor reads and why (legitimate interest);</li>
 *   <li>a <strong>research-participation consent toggle</strong> reflecting the member's current opt-in state,
 *       whose button {@code action_id}s the Slice-5 interactivity handler routes to
 *       {@code ResearchParticipationCommand.setForLogin}; and</li>
 *   <li>a <strong>quiet-hours</strong> control (its write path + persistence land in Slice 5).</li>
 * </ul>
 *
 * <p>For a member who has not linked their identity yet the consent toggle is meaningless, so the Home tab
 * leads with the same "Link Slack" CTA the onboarding service owns (single source of truth). Best-effort — a
 * missing connection or a Slack failure is logged and swallowed (the events controller ACKs within 3s
 * regardless). The live {@code views.publish} round-trip is verified against Slack; the view assembly + the
 * routing decision are deterministic.
 */
@Service
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackAppHomeService {

    private static final Logger log = LoggerFactory.getLogger(SlackAppHomeService.class);

    /** Stable action_ids the Slice-5 interactivity handler binds to; the App Home only renders them today. */
    static final String ACTION_RESEARCH_OPT_OUT = "research_opt_out";
    static final String ACTION_RESEARCH_OPT_IN = "research_opt_in";
    static final String ACTION_QUIET_HOURS = "open_quiet_hours";

    private final SlackWorkspaceResolver workspaceResolver;
    private final SlackMentorIdentityResolver identityResolver;
    private final AccountPreferencesQuery preferencesQuery;
    private final SlackMessageService messageService;
    private final SlackOnboardingService onboardingService;

    public SlackAppHomeService(
        SlackWorkspaceResolver workspaceResolver,
        SlackMentorIdentityResolver identityResolver,
        AccountPreferencesQuery preferencesQuery,
        SlackMessageService messageService,
        SlackOnboardingService onboardingService
    ) {
        this.workspaceResolver = workspaceResolver;
        this.identityResolver = identityResolver;
        this.preferencesQuery = preferencesQuery;
        this.messageService = messageService;
        this.onboardingService = onboardingService;
    }

    /**
     * Handle an {@code app_home_opened} (Home tab) event: assemble and publish the Home view for the opening
     * member. Best-effort; never throws.
     *
     * @param teamId      the Slack {@code T…} workspace id from the verified event envelope
     * @param slackUserId the {@code U…} member who opened the App Home
     */
    public void onHomeOpened(String teamId, String slackUserId) {
        if (teamId == null || teamId.isBlank() || slackUserId == null || slackUserId.isBlank()) {
            return;
        }
        Optional<Long> workspaceId = workspaceResolver.resolveWorkspaceId(teamId);
        if (workspaceId.isEmpty()) {
            log.debug("slack.apphome: app_home_opened for team={} with no active connection — skipping", teamId);
            return;
        }
        long ws = workspaceId.get();
        try {
            messageService.publishHomeView(ws, slackUserId, buildHomeView(ws, teamId, slackUserId));
        } catch (SlackSendException e) {
            log.warn("slack.apphome: views.publish failed for workspace={}, slackError={}", ws, e.slackError());
        }
    }

    /** Assemble the Home view: header + disclosure + (link CTA | consent toggle) + quiet-hours. */
    View buildHomeView(long workspaceId, String teamId, String slackUserId) {
        List<LayoutBlock> blocks = new ArrayList<>();
        blocks.add(header(h -> h.text(plainText("Hephaestus practice mentor"))));
        blocks.addAll(disclosureBlocks());
        blocks.add(divider());

        Optional<String> login = identityResolver.resolveDeveloperLogin(workspaceId, teamId, slackUserId);
        if (login.isEmpty()) {
            // Not linked — a consent toggle without an identity has nothing to act on. Lead with the link CTA.
            blocks.addAll(onboardingService.linkCtaBlocks());
        } else {
            boolean participating = preferencesQuery
                .preferencesForLogin(login.get())
                .map(AccountPreferencesQuery.PreferencesView::participateInResearch)
                .orElse(true); // opt-out model: default participation is on until the user turns it off
            blocks.addAll(consentToggleBlocks(participating));
        }

        blocks.add(divider());
        blocks.addAll(quietHoursBlocks());
        return View.builder().type("home").blocks(blocks).build();
    }

    /** The legitimate-interest privacy disclosure: what the mentor reads and how consent works. */
    List<LayoutBlock> disclosureBlocks() {
        return List.of(
            section(s ->
                s.text(
                    markdownText(
                        "*Your privacy.* The practice mentor reads your work in this workspace to give you " +
                            "feedback on software practices. Messages are only read in channels your workspace " +
                            "explicitly turns on, and only while you take part. You can stop taking part at any " +
                            "time using the toggle below."
                    )
                )
            )
        );
    }

    /** The research-participation consent toggle, reflecting the member's current state. */
    List<LayoutBlock> consentToggleBlocks(boolean participating) {
        String status = participating
            ? "*Research participation:* on. Your anonymised practice data helps improve the mentor."
            : "*Research participation:* off. Your practice data is not used for research.";
        return List.of(
            section(s -> s.text(markdownText(status))),
            actions(a ->
                a.elements(
                    asElements(
                        participating
                            ? button(b ->
                                  b
                                      .text(plainText("Opt out of research"))
                                      .actionId(ACTION_RESEARCH_OPT_OUT)
                                      .value("false")
                                      .style("danger")
                              )
                            : button(b ->
                                  b
                                      .text(plainText("Opt in to research"))
                                      .actionId(ACTION_RESEARCH_OPT_IN)
                                      .value("true")
                                      .style("primary")
                              )
                    )
                )
            )
        );
    }

    /** Quiet-hours control. Rendered here; its write path + persistence land in Slice 5. */
    List<LayoutBlock> quietHoursBlocks() {
        return List.of(
            section(s ->
                s.text(
                    markdownText("*Quiet hours.* Choose a window when the mentor will not send you direct messages.")
                )
            ),
            actions(a ->
                a.elements(asElements(button(b -> b.text(plainText("Set quiet hours")).actionId(ACTION_QUIET_HOURS))))
            )
        );
    }
}
