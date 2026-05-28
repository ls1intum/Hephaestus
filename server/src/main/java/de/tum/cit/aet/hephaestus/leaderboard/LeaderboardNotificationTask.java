package de.tum.cit.aet.hephaestus.leaderboard;

/**
 * Cron-driven leaderboard notification task. Implementations fan-out the weekly digest to
 * an external channel (Slack today; Teams/Discord/email in the future). The leaderboard
 * scheduler picks up every registered impl and wires it onto the configured cron so the
 * scheduler stays kind-agnostic.
 *
 * <p>Marker over {@link Runnable} for two reasons: it makes the contract greppable
 * ("who notifies leaderboards?") and it disambiguates from generic Runnables in the
 * application context. Failures inside {@link #run()} must NOT block other tasks; the
 * scheduler treats each task as independent.
 */
public interface LeaderboardNotificationTask extends Runnable {}
