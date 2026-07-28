package de.tum.cit.aet.hephaestus.workspace;

/**
 * Who, inside a workspace, may see the anonymised practice-health aggregate.
 *
 * <p>A privacy control with a deliberately narrow blast radius: it gates <em>only</em> the aggregate. In both
 * values the roster and the per-developer drill-down — the surfaces that name individuals — stay ADMIN/OWNER
 * only, and every developer always sees their own report. The only thing this widens is who can see how the
 * team as a whole is doing.
 *
 * <ul>
 *   <li>{@link #MENTORS_ONLY} — the default, because opening a team-wide view is a decision a workspace
 *       should make rather than inherit.
 *   <li>{@link #EVERYONE} — members see the anonymised aggregate too. Worth choosing when practice health is
 *       something the team improves together rather than something reported upward.
 * </ul>
 */
public enum HealthVisibility {
    /** Default. Only admins and owners see the health aggregate. */
    MENTORS_ONLY,
    /** Members see the anonymised health aggregate as well; the roster and drill-down stay admin/owner-only. */
    EVERYONE,
}
