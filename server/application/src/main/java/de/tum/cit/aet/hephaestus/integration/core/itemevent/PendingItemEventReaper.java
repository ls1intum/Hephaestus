package de.tum.cit.aet.hephaestus.integration.core.itemevent;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.integration.core.api.ItemEventRecorded;
import de.tum.cit.aet.hephaestus.integration.core.spi.ReviewRequestReason;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnServerRole
@ConditionalOnBooleanProperty(RuntimeRole.AGENT_ENABLED_PROPERTY)
@WorkspaceAgnostic("Selects due item events for all workspaces; consumers retain each event's workspace")
@RequiredArgsConstructor
public class PendingItemEventReaper {
    private final ItemEventRepository repository;
    private final ItemEventLedgerProperties properties;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    @Scheduled(fixedDelay = 15, initialDelay = 5, timeUnit = TimeUnit.MINUTES)
    @SchedulerLock(name = "pending-item-event-reaper", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void sweep() {
        Instant now = clock.instant();
        repository.expire(
                now.minus(properties.pendingLapseAfter()), now, ReviewRequestReason.PENDING_DEADLINE_EXCEEDED);
        repository.deleteUnreferenced(now.minus(properties.retainFor()), properties.sweepBatchSize());
        for (var event : repository.claimDue(now, properties.sweepBatchSize())) {
            repository.attempted(event.getWorkspaceId(), event.getId(), now, now.plus(properties.pendingRetryAfter()));
            publisher.publishEvent(new ItemEventRecorded(event.getWorkspaceId(), event.getId(), event.getItemId()));
        }
    }
}
