package de.tum.cit.aet.hephaestus.core.tenancy;

/**
 * Enforcement mode for {@link WorkspaceStatementInspector}. Configured via
 * {@code hephaestus.tenancy.enforcement}; defaults to {@link #THROW} in test, {@link #LOG}
 * elsewhere. Production flips to {@link #THROW} after a staging canary week of clean
 * {@code tenancy.violation.total} counter readings.
 */
public enum TenancyEnforcement {
    /** Throw {@code TenancyViolationException} on any unguarded scoped-table query. */
    THROW,
    /** Log a WARN and increment {@code tenancy.violation.total}, but let the query through. */
    LOG,
    /** Disable inspection entirely. Use only to debug performance regressions. */
    OFF,
}
