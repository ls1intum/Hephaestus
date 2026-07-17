package de.tum.cit.aet.hephaestus.core.settings.spi;

/**
 * Read port for the instance-wide emergency silent mode. Outbound choke points consult it immediately
 * before writing to an external system and skip while engaged: practice feedback on PRs/MRs
 * ({@code FeedbackDeliveryService}) and issues ({@code IssueReviewHandler}), Slack sends
 * ({@code SlackMessageService}), and the command-ack reaction ({@code BotCommandProcessor}). Reads hit
 * the singleton row directly (no cache) so an engage takes effect on the next write — a cache would
 * add a staleness window in the unsafe direction (still sending just after an engage), which defeats
 * an emergency brake. A DB failure on this read therefore fails closed (the write is skipped), the
 * correct direction for a safety control.
 *
 * <p>Scope: this suppresses <em>outbound delivery only</em>. Review jobs still run and persist their
 * findings while engaged; suppression is not a backlog — held-back feedback is not auto-posted on
 * release (the shadow-backlog + replay flow is #1357/#1358).
 */
public interface SilentModeQuery {
    boolean isSilentModeEngaged();
}
