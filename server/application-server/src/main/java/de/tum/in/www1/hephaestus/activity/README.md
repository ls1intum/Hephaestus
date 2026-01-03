# Activity Module

The Activity module implements an **Event Log** pattern for tracking developer
contributions with XP (experience points) scoring.

> **Pattern: Event Log (not Event Sourcing)**
> 
> This module uses an append-only, immutable event log with pre-computed XP values.
> Unlike Event Sourcing where state is derived by replaying events, XP is computed
> once at write time for optimal leaderboard query performance.

## Security Model

> **⚠️ INTERNAL API - NOT FOR DIRECT CONTROLLER USE**

`ActivityEventService.record()` has no authorization checks by design. It is:

- **Package-private** (not `public`) to prevent external access
- Only called by `ActivityEventListener` in response to authenticated domain events
- Invoked after webhook verification and domain validation has already occurred

```text
Authenticated Webhook → MessageHandler → DomainEvent → ActivityEventListener → ActivityEventService
                ↑                                              ↑                        ↑
         Signature verified              Transaction committed            Package-private
```

**If you need to expose event recording via REST:**

1. Create a dedicated controller with `@PreAuthorize`
2. Never expose `ActivityEventService` directly

## Architecture

```text
Domain Events → ActivityEventListener → ActivityEvent table (write path)
                                                ↓
                          LeaderboardXpQueryService ← LeaderboardService (read path)
```

### Key Components

| Component                   | Responsibility                                                              |
| --------------------------- | --------------------------------------------------------------------------- |
| `ActivityEvent`             | Immutable event entity with XP, timestamps, and schema versioning           |
| `ActivityEventService`      | Write path - records events with idempotency guarantees                     |
| `ActivityEventListener`     | Translates domain events (PR created, review submitted) to activity events  |
| `ActivityEventRepository`   | Data access with optimized queries for leaderboard aggregation              |
| `ExperiencePointCalculator` | XP calculation formulas based on complexity and interaction                 |
| `ExperiencePointStrategy`   | Interface for XP calculation (implemented by `ExperiencePointCalculator`)   |
| `DeadLetterEvent`           | Persists failed events for investigation and replay                         |
| `ActivityIntegrityScheduler`| Scheduled integrity verification of event content hashes                    |
| `LeaderboardCacheManager`   | Workspace-scoped cache invalidation for leaderboard data                    |
| `ActivityRetryProperties`   | Externalized retry configuration                                            |

### CQRS Pattern

- **Write path**: Domain events → `ActivityEventListener` → `ActivityEventService` → database
- **Read path**: `LeaderboardXpQueryService` → aggregation queries → `LeaderboardService` → DTO

XP is computed at write time and persisted, not calculated on-the-fly during reads.

## Adding New Event Types

1. Add enum value to `ActivityEventType`:

   ```java
   ISSUE_CREATED("issue.created", 1.0);  // With default XP
   ```

2. Add event handler in `ActivityEventListener`:

   ```java
   @Async
   @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
   public void onIssueCreated(DomainEvent.IssueCreated event) {
       // Fetch entity, calculate XP, record event
   }
   ```

3. Update `ActivityTargetType` if needed.

## XP Calculation Strategy

XP is calculated using a harmonic mean formula that rewards thorough reviews of complex PRs:

```text
XP = (10 × interactionScore × complexityScore) / (interactionScore + complexityScore)
```

### Dismissed Reviews

Dismissed reviews **are included** in XP calculation. The effort of providing feedback
is valuable regardless of whether the review was later dismissed (e.g., due to new commits
making it stale, or the maintainer overriding the decision).

This matches the original leaderboard behavior where all reviews contribute to the score.

### Complexity Tiers

| Raw Score | Tier           | XP Multiplier |
| --------- | -------------- | ------------- |
| < 10      | Simple         | 1             |
| 10-49     | Medium         | 3             |
| 50-99     | Large          | 7             |
| 100-499   | Huge           | 17            |
| ≥ 500     | Overly complex | 33            |

Raw complexity = `(changedFiles × 3) + (commits × 0.5) + additions + deletions) / 10`

### Review Weights

| Action            | Weight |
| ----------------- | ------ |
| Approval          | 2.0    |
| Changes Requested | 2.5    |
| Comment           | 1.5    |

Weights are configurable via `hephaestus.activity.*` properties.

## Schema Evolution

Events include a `schemaVersion` field (default: 1) for forward-compatible evolution:

```java
@Column(name = "schema_version", nullable = false)
private int schemaVersion = 1;
```

**When to increment version:**

- Breaking changes to payload structure
- Changes to XP calculation that affect historical comparisons

**Migration procedure:**

1. Add new version handling in code
2. Create Liquibase migration if column changes needed
3. Document version history in `ActivityEvent` Javadoc

## Idempotency

Events are deduplicated by `(workspace_id, event_key)` unique constraint.

Event key format: `{event_type}:{target_id}:{timestamp_ms}`

Duplicate events are:

- Silently skipped (return `false` from `record()`)
- Counted via `activity.events.duplicate` metric

## Dead Letter Handling

When activity events fail to record after retry exhaustion (3 attempts with exponential backoff),
they are persisted to the `dead_letter_event` table for durability and later investigation.

### Dead Letter Workflow

```text
Event fails after retries → DeadLetterEvent (PENDING) → Investigation → RESOLVED/DISCARDED
```

### Dead Letter Status

| Status      | Description                                           |
| ----------- | ----------------------------------------------------- |
| `PENDING`   | Awaiting investigation or retry                       |
| `RESOLVED`  | Successfully reprocessed and recorded                 |
| `DISCARDED` | Manually marked as unrecoverable (e.g., invalid data) |

### Investigation Queries

```sql
-- Find pending dead letters for a workspace
SELECT * FROM dead_letter_event
WHERE workspace_id = ? AND status = 'PENDING'
ORDER BY created_at ASC;

-- Count pending by event type for dashboards
SELECT event_type, COUNT(*)
FROM dead_letter_event
WHERE status = 'PENDING'
GROUP BY event_type;

-- Find failures by error type
SELECT * FROM dead_letter_event
WHERE error_type = 'org.springframework.dao.TransientDataAccessException'
AND status = 'PENDING';
```

### Resolution

```java
// Mark as resolved after successful reprocessing
deadLetter.markResolved("Reprocessed after database recovery");

// Mark as discarded for unrecoverable errors
deadLetter.markDiscarded("Invalid workspace ID - data corruption");
```

### Cleanup

Resolved/discarded dead letters should be purged after 30 days:

```sql
DELETE FROM dead_letter_event
WHERE status IN ('RESOLVED', 'DISCARDED')
AND resolved_at < NOW() - INTERVAL '30 days';
```

## Metrics

| Metric                            | Type    | Description                              |
| --------------------------------- | ------- | ---------------------------------------- |
| `activity.events.recorded`        | Counter | Successfully recorded events             |
| `activity.events.duplicate`       | Counter | Duplicate events skipped                 |
| `activity.events.failed`          | Counter | Failed event recordings (for alerting)   |
| `activity.dead_letters.persisted` | Counter | Failed events persisted to dead letter   |
| `activity.events.record.duration` | Timer   | Event recording latency                  |
| `activity.integrity.corrupted`    | Counter | Events with failed integrity checks      |
| `activity.integrity.verified`     | Counter | Events successfully verified             |

### Alerting Recommendations

Set up alerts for:

- `activity.events.failed > 0` - Immediate investigation needed
- `activity.dead_letters.persisted > 10 in 5m` - Potential systemic issue
- `activity.integrity.corrupted > 0` - Data tampering or corruption detected

## Reliability Components

### Retry Mechanism

Configure retry behavior via `application.yml`:

```yaml
hephaestus:
  activity:
    retry:
      max-attempts: 3          # Default: 3
      initial-delay-ms: 100    # Default: 100ms
      max-delay-ms: 1000       # Default: 1s
      multiplier: 2.0          # Default: 2.0
```

### Integrity Verification

`ActivityIntegrityScheduler` runs hourly to verify event content hashes:

```yaml
hephaestus:
  activity:
    integrity:
      enabled: true            # Default: true
      sample-size: 100         # Default: 100
      cron: "0 0 * * * *"      # Default: every hour
```

### Cache Management

`LeaderboardCacheManager` provides workspace-scoped cache invalidation:

- Only invalidates cache for the affected workspace
- Tracks cache keys by workspace for efficient eviction
- Provides monitoring metrics via `getTrackedWorkspaceCount()`

## Architecture Decisions

### Module Boundaries

This module is a **pure bounded context** focused exclusively on:

- XP/gamification event logging
- Leaderboard data aggregation support
- Dead letter handling for failed events

Bad practice detection and code health analysis live in the **separate `practices` module**,
following DDD principles where "activity tracking" and "code health analysis" are distinct
domain concerns with different lifecycles and dependencies.

### Why pre-compute XP at write time?

Event sourcing typically derives state by replaying events. We use **Event Log**
pattern instead because:

1. **Performance**: Leaderboard queries use `SUM(xp) GROUP BY` with covering indexes
2. **Simplicity**: No need for projections or snapshot management
3. **Determinism**: XP is immutable once recorded

This is the optimal pattern for gamification systems where query performance
matters more than historical XP recalculation.

## Testing

Run module tests:

```bash
./mvnw test -Dtest="*Activity*Test,*Leaderboard*Test"
```

Key test classes:

- `ActivityEventServiceTest` - Write path with negative XP, duplicates
- `LeaderboardXpQueryServiceTest` - Read path aggregation
- `LeaderboardServiceComparatorTest` - Tie-breaking logic
