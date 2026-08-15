package de.tum.cit.aet.hephaestus.evidence;

/**
 * On whose authority a source use is recorded as permitted.
 *
 * <p>One value, stated rather than assumed: every decision here is an engineering baseline, never signed
 * off by a controller or data-protection officer — recording that explicitly keeps {@code permitsAt}
 * from being read as approval it is not.
 */
public enum SourceUseBasis {
    ENGINEERING_BASELINE,
}
