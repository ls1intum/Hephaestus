/**
 * Practice <b>reports</b> — read-only, criterion-referenced views over the reviewing practices for a workspace:
 *
 * <ul>
 *   <li>a per-developer report card set, shared by the developer's own self-view ({@code /practices/reports/me})
 *       and the mentor drill-down ({@code /practices/reports/{userId}});
 *   <li>a report roster of developers-with-activity carrying a per-practice status + a needs-attention triage
 *       flag (admin/owner-only — it names individuals);
 *   <li>a k-anonymised workspace health aggregate ({@code /practices/health}: how many developers stand at
 *       each status per practice, never per-person).
 * </ul>
 *
 * <p><b>This surface never ranks developers.</b> There is no score, XP, ELO, rank, or position column, and
 * nothing here is ordered by a count of achievements. The only ordering that touches developers is a
 * needs-attention triage sort (a mentor's work-queue), which is not a performance rank.
 *
 * <p>Every read of a named developer's report drill-down (and the developer-naming roster) is recorded in the
 * generalized append-only {@link de.tum.cit.aet.hephaestus.core.audit.DataAccessEvent} disclosure log so a
 * mentor's access stays transparent to the developer(s) they viewed.
 */
package de.tum.cit.aet.hephaestus.practices.report;
