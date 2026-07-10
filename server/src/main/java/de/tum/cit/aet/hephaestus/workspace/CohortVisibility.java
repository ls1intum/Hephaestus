package de.tum.cit.aet.hephaestus.workspace;

/**
 * Per-workspace audience for the k-anonymised <b>cohort aggregate</b> on the practice overview — a privacy
 * control, never a ranking control. This setting gates <em>only</em> whether ordinary members can see the
 * cohort card; it changes nothing else.
 *
 * <p>In <b>both</b> values the roster and the per-developer drill-down (which name individuals) stay
 * ADMIN/OWNER-only, and every developer always sees their own reflection. The only thing this widens is the
 * cohort aggregate's audience:
 *
 * <ul>
 *   <li>{@link #MENTORS_ONLY} — the default. Only admins/owners see the cohort aggregate (plus the
 *       admin-only roster + drill-down). Regular members do not see the cohort card.
 *   <li>{@link #EVERYONE} — regular members additionally see the k-anonymised cohort aggregate (never any
 *       per-person data; the roster + drill-down remain admin/owner-only).
 * </ul>
 */
public enum CohortVisibility {
    /** Default. Only admins/owners see the cohort aggregate; members do not. Roster + drill-down admin-only. */
    MENTORS_ONLY,
    /** Members additionally see the k-anonymised cohort aggregate. Roster + drill-down stay admin/owner-only. */
    EVERYONE,
}
