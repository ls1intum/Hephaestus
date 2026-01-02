# Activity Module

The Activity module implements an event-sourced activity ledger for tracking developer
contributions with XP (experience points) scoring.

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

Dismissed reviews are intentionally excluded from XP calculation.

| Reason                       | Impact                                              |
| ---------------------------- | --------------------------------------------------- |
| Stale review (new commits)   | Original feedback no longer applies                 |
| Rejected by maintainer       | Review quality deemed insufficient                  |
| Overridden decision          | Approval/rejection was overruled                    |

Awarding XP for dismissed reviews would:

- Incentivize low-quality "drive-by" approvals
- Double-count XP when reviewer resubmits
- Reward outdated feedback

See `ExperiencePointCalculator.calculateReviewExperiencePoints()` for implementation.

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

## Metrics

| Metric                            | Type    | Description                            |
| --------------------------------- | ------- | -------------------------------------- |
| `activity.events.recorded`        | Counter | Successfully recorded events           |
| `activity.events.duplicate`       | Counter | Duplicate events skipped               |
| `activity.events.failed`          | Counter | Failed event recordings (for alerting) |
| `activity.events.record.duration` | Timer   | Event recording latency                |

## Testing

Run module tests:

```bash
./mvnw test -Dtest="*Activity*Test,*Leaderboard*Test"
```

Key test classes:

- `ActivityEventServiceTest` - Write path with negative XP, duplicates
- `LeaderboardXpQueryServiceTest` - Read path aggregation
- `LeaderboardServiceComparatorTest` - Tie-breaking logic
