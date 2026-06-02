/**
 * Append-only activity event log used for gamification and leaderboard aggregation.
 *
 * <p>The module owns {@code activity_event} and emits {@code ActivitySavedEvent} on insert.
 * XP is pre-computed at write time so reads are O(1) and leaderboard queries reduce to
 * SUM/GROUP BY against the event table — see {@link de.tum.cit.aet.hephaestus.leaderboard}
 * for the read side.
 *
 * <p>Distinct bounded context from {@link de.tum.cit.aet.hephaestus.practices} (code-health
 * analysis), even though both consume {@code ScmDomainEvent}s.
 *
 * <p>Cross-module callers reach the write path through
 * {@link de.tum.cit.aet.hephaestus.activity.spi.ActivityRecorder} (the {@code spi}
 * NamedInterface) — never through {@code ActivityEventService} directly. Today the
 * sole external caller is the GitHub Projects v2 listener under
 * {@code integration/scm/github/project/activity/}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Activity Event Log")
package de.tum.cit.aet.hephaestus.activity;
