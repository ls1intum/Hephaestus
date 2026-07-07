package de.tum.cit.aet.hephaestus.integration.slack.channel;

import static com.slack.api.model.block.Blocks.actions;
import static com.slack.api.model.block.Blocks.context;
import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.asContextElements;
import static com.slack.api.model.block.element.BlockElements.asElements;
import static com.slack.api.model.block.element.BlockElements.button;

import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.composition.ConfirmationDialogObject;
import java.util.List;

/**
 * The single source of truth for the in-channel Slack consent notice — its plain-language copy and its Block Kit
 * rendering. Rendered in two places so a member always gets the SAME transparency message and the SAME one-click
 * control:
 *
 * <ul>
 *   <li>the one-time <strong>activation announcement</strong> posted to the whole channel on
 *       {@code PENDING → ACTIVE} ({@code SlackChannelConsentService});</li>
 *   <li>the <strong>just-in-time ephemeral notice</strong> shown only to a member who joins an already-active
 *       channel afterwards ({@code SlackChannelJoinNoticeHandler}) — the person the one-time announcement could
 *       never reach.</li>
 * </ul>
 *
 * <p>The copy follows plain-language transparency guidance: it says concretely what is read (new messages, for
 * practice feedback), what is not (earlier history), and gives the member direct control. The {@code "Opt me out"}
 * button carries a Slack {@code confirm} dialog and the stable {@link #ACTION_PARTICIPANT_OPT_OUT} action id the
 * interactivity router binds to the same person opt-out + erase path the App Home "Opt out" button uses.
 *
 * <p>Pure and dependency-free (static block assembly, no Spring): a plain-text fallback rides on the surrounding
 * message so the notice still reads on a client that cannot render the actions.
 */
public final class SlackConsentBlocks {

    /**
     * In-message one-click opt-out. Bound by {@code SlackFeedbackHandler} to the App Home opt-out path (person
     * ingestion opt-out + erase already-collected data), keyed on the acting Slack user id.
     */
    public static final String ACTION_PARTICIPANT_OPT_OUT = "participant_opt_out";

    /** Defensive: a pointer button back to the App Home privacy tab. Re-renders the Home view when clicked. */
    public static final String ACTION_OPEN_PRIVACY_HOME = "open_privacy_home";

    private static final String NOTICE_LINE_1 =
        "*Hephaestus is now reading new messages in this channel* to give people feedback on how the team works " +
        "day to day — code review, testing, and how issues and questions are written. Only messages from now on " +
        "are read; earlier history is never touched.";

    private static final String NOTICE_LINE_2 =
        "You're in control — you can keep *your own* messages out at any time; it also deletes anything already " +
        "collected about you.";

    /** Plain-text fallback for the notice (no mrkdwn), shown in notifications + by accessibility tools. */
    public static final String FALLBACK_TEXT =
        "Hephaestus is now reading new messages in this channel to give people feedback on how the team works day " +
        "to day — code review, testing, and how issues and questions are written. Only messages from now on are " +
        "read; earlier history is never touched. You're in control — you can keep your own messages out at any " +
        "time; it also deletes anything already collected about you.";

    /** Ephemeral confirmation shown to a member right after they opt out via the in-message button. */
    public static final String CONFIRMATION_TEXT =
        "You're opted out — your messages won't be read and anything collected has been deleted.";

    private SlackConsentBlocks() {}

    /**
     * The consent notice: the copy, a danger {@code "Opt me out"} button (with a confirm dialog), and a context
     * pointer to the App Home. Freshly built per call so the returned list is never shared/mutated.
     */
    public static List<LayoutBlock> consentNotice() {
        return List.of(
            section(s -> s.text(markdownText(NOTICE_LINE_1 + "\n\n" + NOTICE_LINE_2))),
            actions(a ->
                a.elements(
                    asElements(
                        button(b ->
                            b
                                .text(plainText("Opt me out"))
                                .actionId(ACTION_PARTICIPANT_OPT_OUT)
                                .style("danger")
                                .confirm(optOutConfirm())
                        )
                    )
                )
            ),
            context(c ->
                c.elements(asContextElements(markdownText("Manage this anytime in the Hephaestus app → *Home* tab.")))
            )
        );
    }

    /** The ephemeral opt-out confirmation shown to the member who just clicked {@code "Opt me out"}. */
    public static List<LayoutBlock> optOutConfirmation() {
        return List.of(section(s -> s.text(markdownText(CONFIRMATION_TEXT))));
    }

    /** The confirm dialog on the opt-out button — spells out the consequence before the irreversible erase. */
    private static ConfirmationDialogObject optOutConfirm() {
        return ConfirmationDialogObject.builder()
            .title(plainText("Opt out of message reading"))
            .text(
                plainText(
                    "Your messages in monitored channels won't be read, and anything already collected about you " +
                        "is deleted. Your own DMs with the mentor are unaffected."
                )
            )
            .confirm(plainText("Opt me out"))
            .deny(plainText("Cancel"))
            .style("danger")
            .build();
    }
}
