/**
 * Practice reports — the read model over observations that people actually look at.
 *
 * <p>Three surfaces, one derivation: a developer's per-practice cards, the mentor roster over those cards at
 * practice-area grain, and the anonymised workspace-health distribution. The cards a mentor sees on the
 * drill-down are the cards the developer sees on their own report, produced by the same method, so the two
 * views cannot drift into "what you see" and "what they see about you".
 *
 * <p><b>Criterion-referenced, permanently</b> (ADR 0028): nothing here carries a score, rank, percentile or
 * total, because norm-referenced comparison between teammates moves the target and costs trust — which is
 * what the removed leaderboard did. {@code NonCompetitiveSurfaceArchTest} pins the wire shape so the
 * invariant survives contributors who never read this file.
 *
 * <p>Sibling of {@code practices.observation}, which owns the raw ledger and the queries. This package owns
 * the interpretation: the window ({@code ReportWindowResolver}), the status/trend derivation
 * ({@code PracticeStatusDeriver}), and the anonymity rules on the aggregate.
 */
package de.tum.cit.aet.hephaestus.practices.report;
