package de.tum.cit.aet.hephaestus.agent.handler;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The persisted identities of one {@link de.tum.cit.aet.hephaestus.practices.model.PracticeResult}, stamped onto a
 * result by the handler so downstream stages address the stored row without recomputing a key. Recurrence is a
 * property of each located problem, not of the result.
 *
 * @param occurrenceKey this result alone ({@code practice_result.occurrence_key}, uniquely constrained) — the key to
 *     use whenever a single result must be addressed
 * @param id the row's id, which the composition stage cites in {@code basedOn}. Null while admission computes the
 *     keys, before the row exists
 */
public record ResultKeys(String occurrenceKey, @Nullable UUID id) {
    public ResultKeys(String occurrenceKey) {
        this(occurrenceKey, null);
    }
}
