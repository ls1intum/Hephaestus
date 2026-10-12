package de.tum.cit.aet.hephaestus.practices.spi;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public interface EvidenceAuthorization {
    Set<UUID> permitsForNewDelivery(
            long workspaceId, Collection<PracticeResult> practiceResults, SourceUsePurpose purpose);

    boolean permits(long workspaceId, PracticeResult practiceResult, SourceUsePurpose purpose);

    /**
     * The ids of the results {@link #permits} would admit, for a whole list at once.
     *
     * <p>Membership is the whole answer: a result whose id is absent is not permitted, so a caller
     * never has to tell "denied" from "not asked about". A result that has not been persisted has no
     * id to return and is therefore never permitted — the safe direction for an authorization answer, and
     * unreachable from the read surfaces, which authorize rows they loaded from the database.
     *
     * @return the permitted result ids; never null, possibly empty
     */
    Set<UUID> permitsAll(long workspaceId, Collection<PracticeResult> practiceResults, SourceUsePurpose purpose);

    /**
     * Runs {@code read} as one read of the workspace for {@code purpose}: every authorization inside it shares
     * its source checks, so a source cited by several of the read's queries is checked once. Changes no answer.
     */
    default <T> T asOneRead(long workspaceId, SourceUsePurpose purpose, Supplier<T> read) {
        return read.get();
    }
}
