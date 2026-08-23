package de.tum.cit.aet.hephaestus.practices.spi;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import java.util.Optional;

/**
 * Who the current request is acting as, as the developer id the practices module files observations
 * and feedback against.
 *
 * <p>Practices needs one fact about the caller — their developer id — and used to reach into the SCM
 * user store for it. That coupled a learner-facing read model to an integration's entity, so the whole
 * module could not be reasoned about without it. The id is deliberately the only thing this port
 * returns: a caller that wants a name, an avatar, or a provider identity is asking a question that
 * belongs to whoever owns users, not to a practice surface.
 *
 * <p>Absent is a normal answer, not an error: a person who has signed in but has never been synced as
 * a developer has no id yet, and every read surface answers them with an empty result rather than a
 * failure. Only a write needs {@link #currentDeveloperIdElseThrow()}.
 */
public interface CurrentDeveloperLookup {
    /** The current caller's developer id, or empty when they are not (yet) a synced developer. */
    Optional<Long> currentDeveloperId();

    /**
     * The current caller's developer id.
     *
     * @throws EntityNotFoundException when the caller is not a synced developer — for a write there is
     *     no meaningful empty answer, since the row would have no owner.
     */
    long currentDeveloperIdElseThrow();
}
