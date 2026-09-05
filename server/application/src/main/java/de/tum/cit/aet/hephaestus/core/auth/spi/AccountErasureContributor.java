package de.tum.cit.aet.hephaestus.core.auth.spi;

/**
 * SPI for modules that own rows keyed by an account, so account erasure reaches them without
 * {@code core.auth} naming another module's tables.
 *
 * <p>An account is kept as a tombstone rather than deleted, so an {@code ON DELETE CASCADE} on
 * {@code account_id} never fires and every owning module has to erase its own rows.
 */
public interface AccountErasureContributor {
    /**
     * Delete the data this module holds for the given account.
     *
     * <p>Called inside the account-purge transaction: a failure rolls the whole purge back and leaves
     * the account eligible for the next sweep.
     *
     * @param accountId the account being erased
     */
    void eraseAccount(long accountId);
}
