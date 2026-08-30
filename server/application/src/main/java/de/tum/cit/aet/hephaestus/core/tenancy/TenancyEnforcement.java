package de.tum.cit.aet.hephaestus.core.tenancy;

/** Enforcement mode for {@link WorkspaceStatementInspector}. */
public enum TenancyEnforcement {
    /** Throw {@code TenancyViolationException} on any unguarded scoped-table query. */
    THROW,
    /** Log a WARN and increment {@code tenancy.violation.total}, but let the query through. */
    LOG,
    /** Disable inspection entirely. Use only to debug performance regressions. */
    OFF,
}
