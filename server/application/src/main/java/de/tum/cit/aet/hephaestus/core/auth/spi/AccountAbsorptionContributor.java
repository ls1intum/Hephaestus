package de.tum.cit.aet.hephaestus.core.auth.spi;

public interface AccountAbsorptionContributor {
    int order();

    void absorb(long claimedAccountId, long unclaimedAccountId);

    void finish(long claimedAccountId, long unclaimedAccountId);
}
