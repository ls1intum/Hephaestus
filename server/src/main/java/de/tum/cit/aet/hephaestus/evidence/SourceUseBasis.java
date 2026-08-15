package de.tum.cit.aet.hephaestus.evidence;

/**
 * On whose authority a source use is recorded as permitted.
 *
 * <p>One value, and stated rather than assumed: every decision this product ships is an engineering
 * baseline, so nothing a review reads has been signed off by a controller or a data-protection
 * officer. Recording that explicitly is what keeps {@code permitsAt} from ever being read as approval
 * it is not.
 */
public enum SourceUseBasis {
    ENGINEERING_BASELINE,
}
