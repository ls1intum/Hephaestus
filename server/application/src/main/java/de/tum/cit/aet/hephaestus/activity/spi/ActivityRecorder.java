package de.tum.cit.aet.hephaestus.activity.spi;

import de.tum.cit.aet.hephaestus.activity.ActivityEventType;
import de.tum.cit.aet.hephaestus.activity.ActivityTargetType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Narrow seam through which non-{@code activity} modules append rows to the activity
 * ledger. Mirrors the two {@code record(...)}/{@code recordDeleted(...)} entry-points on
 * {@code ActivityEventService} — same parameters, same idempotency semantics, same
 * fire-and-forget return contract.
 *
 * <p>Implemented exclusively by {@code ActivityEventService}. The interface exists so that
 * vendor adapters (today: GitHub Projects v2 listener under
 * {@code integration/scm/github/project/activity/}) can call into the activity write path
 * without importing the {@code activity} module's implementation classes — the
 * {@code activity::spi} named interface is the only cross-module surface of activity-write.
 *
 * <p>Do NOT add domain-specific overloads here. If a new caller needs a richer shape,
 * extend {@code ActivityEventService}'s public surface AND mirror it here verbatim.
 */
public interface ActivityRecorder {
    /**
     * @see de.tum.cit.aet.hephaestus.activity.ActivityEventService#record(Long, ActivityEventType, Instant, User, Repository, ActivityTargetType, Long, double)
     */
    boolean record(
        Long workspaceId,
        ActivityEventType eventType,
        Instant occurredAt,
        @Nullable User actor,
        @Nullable Repository repository,
        ActivityTargetType targetType,
        Long targetId,
        double xp
    );

    /**
     * @see de.tum.cit.aet.hephaestus.activity.ActivityEventService#recordDeleted(Long, ActivityEventType, Instant, ActivityTargetType, Long)
     */
    boolean recordDeleted(
        Long workspaceId,
        ActivityEventType eventType,
        Instant occurredAt,
        ActivityTargetType targetType,
        Long targetId
    );
}
