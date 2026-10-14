package de.tum.cit.aet.hephaestus.core.auth.spi;

public interface AccountAbsorptionContributor {
    /** Prepare owner-specific constraints before any person rows move. */
    default void beforeAbsorb(long claimedAccountId, long unclaimedAccountId) {}

    void absorb(long claimedAccountId, long unclaimedAccountId);

    /** Validate owner-specific constraints after all person rows move and before cleanup. */
    default void afterAbsorb(long claimedAccountId, long unclaimedAccountId) {}

    default void finish(long claimedAccountId, long unclaimedAccountId) {}
}
