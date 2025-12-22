# GitProvider Module Refactoring Plan

## Executive Summary

This document outlines a complete architectural refactoring of the `gitprovider` module to:
1. **Eliminate hub4j (github-api) dependency** - Replace with GraphQL API + direct JSON parsing
2. **Implement event-driven architecture** - Enable reactive feature development
3. **Establish anti-corruption layer** - DTOs that shield domain from external APIs
4. **Standardize feature-grouped structure** - Consistent patterns across all entities

---

## Current State Analysis

### Problems with Current Architecture

1. **hub4j Coupling**: 67 files depend on `org.kohsuke.github` types
2. **Live Objects**: `GHIssue`, `GHPullRequest` etc. make API calls when accessing properties
3. **Missing Fields**: hub4j drops unknown fields like `issueType` due to `@JsonIgnoreProperties`
4. **Duplicated Logic**: Each entity has separate sync/webhook processing paths
5. **No Event System**: Features like BadPracticeDetector use polling instead of reacting to events
6. **Inconsistent Structure**: Different patterns across different entities

### hub4j Usage Breakdown

| Import | Count | Entities Affected |
|--------|-------|-------------------|
| `GHEvent` | 15 | All webhook handlers |
| `GHUser` | 14 | Issues, PRs, Reviews, Comments |
| `GHRepository` | 13 | All sync services |
| `GHEventPayload` | 11 | All message handlers |
| `GitHub` | 10 | Client providers, sync services |
| `GHIssue` | 4 | Issue sync, converters |
| `GHPullRequest` | 4 | PR sync, converters |
| `GHTeam` | 4 | Team sync |
| Other types | 30+ | Various |

---

## Target Architecture

### Core Design Principles

1. **Feature-Grouped**: All code for an entity lives in its package
2. **Anti-Corruption Layer**: DTOs translate external formats to domain models
3. **Event-Driven**: Processing emits domain events for reactive listeners
4. **Single Processing Path**: Both sync and webhooks use the same processor
5. **GraphQL-First**: Use GraphQL for sync (precise field selection)
6. **Direct JSON for Webhooks**: Parse webhook payloads directly to DTOs

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                            EXTERNAL DATA SOURCES                                     │
├───────────────────────────────────────┬─────────────────────────────────────────────┤
│     GitHub GraphQL API                │         GitHub Webhooks                     │
│     (Scheduled Sync)                  │         (Real-time Events)                  │
└──────────────────┬────────────────────┴────────────────────┬────────────────────────┘
                   │                                          │
                   ▼                                          ▼
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                              INGESTION LAYER                                          │
│                                                                                       │
│   ┌─────────────────────────────┐    ┌─────────────────────────────┐                │
│   │    GitHubXxxFetcher         │    │    GitHubXxxParser          │                │
│   │    (GraphQL queries)        │    │    (JSON → DTO)             │                │
│   │                             │    │                             │                │
│   │  - Uses HttpGraphQlClient   │    │  - Uses Jackson ObjectMapper│                │
│   │  - Handles pagination       │    │  - Validates payloads       │                │
│   │  - Returns List<XxxDTO>     │    │  - Returns XxxEventDTO      │                │
│   └─────────────────────────────┘    └─────────────────────────────┘                │
└──────────────────────────────────────────┬───────────────────────────────────────────┘
                                           │
                                           ▼
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                        ANTI-CORRUPTION LAYER (DTOs)                                   │
│                                                                                       │
│   gitprovider/xxx/github/dto/                                                        │
│   ├── GitHubXxxDTO.java           # Data model (matches GraphQL + REST)              │
│   ├── GitHubXxxEventDTO.java      # Webhook event wrapper (action + payload)         │
│   └── Supporting DTOs...          # User, Label, Milestone refs                      │
│                                                                                       │
│   Properties:                                                                         │
│   - Plain Java records with Jackson annotations                                       │
│   - NO hub4j types, NO external dependencies                                          │
│   - Unified format for GraphQL and webhook JSON                                       │
└──────────────────────────────────────────┬───────────────────────────────────────────┘
                                           │
                                           ▼
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                            PROCESSING LAYER                                           │
│                                                                                       │
│   ┌─────────────────────────────────────────────────────────────────────────────┐   │
│   │                        GitHubXxxProcessor                                    │   │
│   │                                                                              │   │
│   │  @Service                                                                    │   │
│   │  public class GitHubXxxProcessor {                                          │   │
│   │                                                                              │   │
│   │      private final XxxRepository xxxRepository;                              │   │
│   │      private final ApplicationEventPublisher eventPublisher;                 │   │
│   │      // ... other repositories for relationships                            │   │
│   │                                                                              │   │
│   │      @Transactional                                                          │   │
│   │      public Xxx process(GitHubXxxDTO dto, Context ctx) {                    │   │
│   │          boolean isNew = !xxxRepository.existsById(dto.getDatabaseId());    │   │
│   │          Xxx entity = mapToEntity(dto, ctx);                                 │   │
│   │          entity = xxxRepository.save(entity);                                │   │
│   │                                                                              │   │
│   │          // Emit domain events                                               │   │
│   │          if (isNew) {                                                        │   │
│   │              eventPublisher.publishEvent(new XxxCreatedEvent(entity));      │   │
│   │          } else {                                                            │   │
│   │              eventPublisher.publishEvent(new XxxUpdatedEvent(entity));      │   │
│   │          }                                                                   │   │
│   │          return entity;                                                      │   │
│   │      }                                                                       │   │
│   │  }                                                                           │   │
│   └─────────────────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────┬───────────────────────────────────────────┘
                                           │
                                           ▼
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                         DOMAIN EVENT BUS                                              │
│                     (Spring ApplicationEventPublisher)                                │
│                                                                                       │
│   Events are published synchronously within the transaction.                          │
│   Listeners can use @TransactionalEventListener with AFTER_COMMIT phase.             │
│                                                                                       │
│   Event Types per Entity:                                                             │
│   - XxxCreatedEvent      → emitted when entity is created                            │
│   - XxxUpdatedEvent      → emitted when entity is updated                            │
│   - XxxDeletedEvent      → emitted when entity is deleted                            │
│   - Entity-specific events (e.g., PullRequestMergedEvent, IssueTypedEvent)           │
└──────────────────────────────────────────┬───────────────────────────────────────────┘
                                           │
                                           ▼
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                           EVENT LISTENERS                                             │
│                                                                                       │
│   Features can subscribe to domain events without coupling to git provider:           │
│                                                                                       │
│   @Component                                                                          │
│   public class BadPracticeEventListener {                                            │
│       @Async                                                                          │
│       @TransactionalEventListener(phase = AFTER_COMMIT)                              │
│       public void onPullRequestCreated(PullRequestCreatedEvent event) {              │
│           detector.detect(event.pullRequest());                                       │
│       }                                                                               │
│   }                                                                                   │
│                                                                                       │
│   Potential Listeners:                                                                │
│   - BadPracticeEventListener → triggers detection on PR events                       │
│   - NotificationEventListener → sends notifications on important events              │
│   - MetricsEventListener → updates leaderboard/contribution metrics                  │
│   - AuditEventListener → logs activity for workspace auditing                        │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

### Orchestration Layer

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                          ORCHESTRATION LAYER                                          │
│                                                                                       │
│   ┌────────────────────────────────────┐  ┌────────────────────────────────────┐    │
│   │      GitHubXxxSyncService          │  │    GitHubXxxMessageHandler         │    │
│   │      (Scheduled Sync)              │  │    (Webhook Processing)            │    │
│   │                                    │  │                                    │    │
│   │  - Called by scheduler             │  │  - Receives NATS messages          │    │
│   │  - Uses Fetcher to get DTOs        │  │  - Uses Parser to get DTOs         │    │
│   │  - Passes DTOs to Processor        │  │  - Passes DTOs to Processor        │    │
│   │  - Handles pagination/cooldowns    │  │  - Handles event routing           │    │
│   └────────────────────────────────────┘  └────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Critical Infrastructure Components

### 1. Webhook Deduplication Service

GitHub uses "at-least-once" delivery, meaning the same webhook can arrive multiple times.
We MUST deduplicate using the `X-GitHub-Delivery` header.

```java
@Service
public class WebhookDeduplicationService {
    
    private final WebhookDeliveryRepository deliveryRepository;
    private static final Duration RETENTION_PERIOD = Duration.ofDays(7);
    
    /**
     * Check if this webhook delivery has already been processed.
     * Returns true if this is a new, unprocessed delivery.
     */
    @Transactional
    public boolean markAsProcessedIfNew(String deliveryId) {
        if (deliveryRepository.existsById(deliveryId)) {
            log.debug("Duplicate webhook delivery ignored: {}", deliveryId);
            return false;
        }
        deliveryRepository.save(new WebhookDelivery(deliveryId, Instant.now()));
        return true;
    }
    
    /**
     * Scheduled cleanup of old delivery records.
     */
    @Scheduled(cron = "0 0 3 * * *")  // 3 AM daily
    @Transactional
    public void cleanupOldDeliveries() {
        Instant cutoff = Instant.now().minus(RETENTION_PERIOD);
        int deleted = deliveryRepository.deleteByProcessedAtBefore(cutoff);
        log.info("Cleaned up {} old webhook delivery records", deleted);
    }
}

@Entity
@Table(name = "webhook_delivery")
public class WebhookDelivery {
    @Id
    private String id;  // X-GitHub-Delivery UUID
    
    private Instant processedAt;
}
```

**Usage in Message Handlers:**
```java
@Override
protected void handleEvent(GHEvent event, String payload, String deliveryId) {
    if (!deduplicationService.markAsProcessedIfNew(deliveryId)) {
        return;  // Already processed, skip
    }
    // Process the event...
}
```

### 2. Rate Limit Tracker

GitHub's GraphQL API uses a points-based rate limit (5,000/hour for PATs, more for GitHub Apps).
We must track and respect these limits.

```java
@Service
public class GitHubRateLimitTracker {
    
    private final Map<Long, RateLimitState> workspaceLimits = new ConcurrentHashMap<>();
    
    public record RateLimitState(
        int remaining,
        int limit,
        Instant resetAt,
        Instant lastUpdated
    ) {}
    
    /**
     * Check if we can execute a query with estimated cost.
     */
    public boolean canExecuteQuery(Long workspaceId, int estimatedCost) {
        RateLimitState state = workspaceLimits.get(workspaceId);
        if (state == null) {
            return true;  // Unknown state, allow
        }
        
        if (Instant.now().isAfter(state.resetAt())) {
            return true;  // Limit has reset
        }
        
        return state.remaining() >= estimatedCost;
    }
    
    /**
     * Update rate limit state from GraphQL response headers.
     */
    public void updateFromResponse(Long workspaceId, int remaining, int limit, Instant resetAt) {
        workspaceLimits.put(workspaceId, new RateLimitState(
            remaining, limit, resetAt, Instant.now()
        ));
        
        if (remaining < 100) {
            log.warn("GitHub rate limit low for workspace {}: {}/{}", 
                workspaceId, remaining, limit);
        }
    }
    
    /**
     * Calculate wait time if rate limited.
     */
    public Duration getWaitDuration(Long workspaceId) {
        RateLimitState state = workspaceLimits.get(workspaceId);
        if (state == null || state.remaining() > 0) {
            return Duration.ZERO;
        }
        return Duration.between(Instant.now(), state.resetAt());
    }
}
```

### 3. Resilience4j Configuration

Add circuit breakers and retry logic for all GitHub API calls.

**application.yml:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      githubGraphQL:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
      githubREST:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s

  retry:
    instances:
      githubApi:
        maxAttempts: 3
        waitDuration: 1s
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.net.SocketTimeoutException
          - org.springframework.web.reactive.function.client.WebClientRequestException
        ignoreExceptions:
          - org.kohsuke.github.GHFileNotFoundException

  ratelimiter:
    instances:
      githubGraphQL:
        limitForPeriod: 30          # ~2000 points/min, assuming 67 points/query
        limitRefreshPeriod: 60s
        timeoutDuration: 30s
```

**Usage in Fetcher:**
```java
@Service
public class GitHubIssueFetcher {
    
    @CircuitBreaker(name = "githubGraphQL", fallbackMethod = "fallbackFetchIssues")
    @Retry(name = "githubApi")
    @RateLimiter(name = "githubGraphQL")
    public List<GitHubIssueDTO> fetchIssues(Repository repository, ProcessingContext ctx) {
        // GraphQL query
    }
    
    private List<GitHubIssueDTO> fallbackFetchIssues(
            Repository repository, 
            ProcessingContext ctx, 
            Exception e) {
        log.error("GitHub API unavailable, skipping issue sync for {}", 
            repository.getNameWithOwner(), e);
        return List.of();
    }
}
```

### 4. Processing Context

A unified context object passed through the processing pipeline.

```java
@Value
@Builder
public class ProcessingContext {
    Long workspaceId;
    Repository repository;
    Instant syncStartedAt;
    
    // For webhooks
    @Nullable String webhookDeliveryId;
    @Nullable String webhookAction;
    
    // For tracking
    String correlationId;  // For distributed tracing
    
    public static ProcessingContext forSync(Workspace workspace, Repository repository) {
        return ProcessingContext.builder()
            .workspaceId(workspace.getId())
            .repository(repository)
            .syncStartedAt(Instant.now())
            .correlationId(UUID.randomUUID().toString())
            .build();
    }
    
    public static ProcessingContext forWebhook(
            Workspace workspace, 
            Repository repository,
            String deliveryId,
            String action) {
        return ProcessingContext.builder()
            .workspaceId(workspace.getId())
            .repository(repository)
            .syncStartedAt(Instant.now())
            .webhookDeliveryId(deliveryId)
            .webhookAction(action)
            .correlationId(deliveryId)  // Use delivery ID for correlation
            .build();
    }
}
```

### 5. Domain Events vs Integration Events

**Domain Events** are in-process, synchronous, within the same transaction.
**Integration Events** are cross-service, asynchronous, via NATS.

```java
// Domain Event - published within transaction, handled locally
public record IssueCreatedEvent(Issue issue, ProcessingContext context) {}

// Integration Event - published to NATS after commit, handled by other services
public record IssueCreatedIntegrationEvent(
    long issueId,
    String nodeId,
    int number,
    String repositoryFullName,
    Long workspaceId,
    Instant occurredAt
) {}

// Bridge that listens to domain events and publishes integration events
@Component
public class IssueIntegrationEventPublisher {
    
    private final NatsTemplate natsTemplate;
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onIssueCreated(IssueCreatedEvent event) {
        var integrationEvent = new IssueCreatedIntegrationEvent(
            event.issue().getId(),
            event.issue().getNodeId(),
            event.issue().getNumber(),
            event.issue().getRepository().getNameWithOwner(),
            event.context().getWorkspaceId(),
            Instant.now()
        );
        natsTemplate.publish("github.issue.created", integrationEvent);
    }
}
```

---


## Package Structure

### Per-Entity Package Layout

```
gitprovider/
├── common/
│   └── github/
│       ├── GitHubGraphQlClientProvider.java       # GraphQL client factory
│       ├── GitHubObjectMapper.java                # Jackson config for webhooks
│       └── GitHubConfig.java                      # Common GitHub configs
│
├── issue/
│   ├── Issue.java                                 # JPA Entity
│   ├── IssueRepository.java                       # Spring Data Repository
│   ├── IssueState.java                            # Domain enum
│   ├── IssueInfoDTO.java                          # API response DTO
│   │
│   ├── github/
│   │   ├── dto/
│   │   │   ├── GitHubIssueDTO.java                # Issue data from API
│   │   │   ├── GitHubIssueEventDTO.java           # Webhook event wrapper
│   │   │   ├── GitHubUserRefDTO.java              # User reference
│   │   │   ├── GitHubLabelRefDTO.java             # Label reference
│   │   │   └── GitHubMilestoneRefDTO.java         # Milestone reference
│   │   │
│   │   ├── GitHubIssueFetcher.java                # GraphQL queries
│   │   ├── GitHubIssueParser.java                 # JSON → DTO
│   │   ├── GitHubIssueProcessor.java              # DTO → Entity + events
│   │   ├── GitHubIssueSyncService.java            # Sync orchestration
│   │   └── GitHubIssueMessageHandler.java         # Webhook orchestration
│   │
│   └── events/
│       ├── IssueCreatedEvent.java
│       ├── IssueUpdatedEvent.java
│       ├── IssueClosedEvent.java
│       ├── IssueReopenedEvent.java
│       └── IssueTypedEvent.java
│
├── pullrequest/
│   ├── PullRequest.java
│   ├── PullRequestRepository.java
│   │
│   ├── github/
│   │   ├── dto/
│   │   │   ├── GitHubPullRequestDTO.java
│   │   │   └── GitHubPullRequestEventDTO.java
│   │   │
│   │   ├── GitHubPullRequestFetcher.java
│   │   ├── GitHubPullRequestParser.java
│   │   ├── GitHubPullRequestProcessor.java
│   │   ├── GitHubPullRequestSyncService.java
│   │   └── GitHubPullRequestMessageHandler.java
│   │
│   └── events/
│       ├── PullRequestCreatedEvent.java
│       ├── PullRequestUpdatedEvent.java
│       ├── PullRequestMergedEvent.java
│       ├── PullRequestClosedEvent.java
│       ├── PullRequestDraftChangedEvent.java
│       ├── PullRequestLabelChangedEvent.java
│       └── PullRequestReviewRequestedEvent.java
│
├── (similar structure for each entity...)
│
└── sync/
    ├── GitHubDataSyncService.java                 # Coordinates all syncs
    └── GitHubDataSyncScheduler.java               # Scheduling triggers
```

---

## Entities to Migrate

### Priority 1: Issues (Start Here)

| Component | Current | Target |
|-----------|---------|--------|
| DTO | None (uses GHIssue) | GitHubIssueDTO, GitHubIssueEventDTO |
| Fetcher | Uses hub4j REST | GitHubIssueFetcher (GraphQL) |
| Parser | Uses GHEventPayload | GitHubIssueParser (Jackson) |
| Processor | GitHubIssueConverter + GitHubIssueSyncService | GitHubIssueProcessor |
| Events | None | IssueCreatedEvent, IssueUpdatedEvent, IssueTypedEvent |

**GraphQL Query**: `GetRepositoryIssues.graphql` (already created)

### Priority 2: Pull Requests

| Component | Current | Target |
|-----------|---------|--------|
| DTO | None (uses GHPullRequest) | GitHubPullRequestDTO |
| Fetcher | Uses hub4j REST | GitHubPullRequestFetcher (GraphQL) |
| Processor | GitHubPullRequestConverter + SyncService | GitHubPullRequestProcessor |
| Events | None | PullRequestCreatedEvent, PullRequestMergedEvent, etc. |

**Special Events Needed**:
- `PullRequestMergedEvent` - for merge tracking
- `PullRequestLabelChangedEvent` - for lifecycle (ready-to-review, ready-to-merge)
- `PullRequestReviewRequestedEvent` - for review workflow

### Priority 3: Pull Request Reviews

| Component | Target |
|-----------|--------|
| DTO | GitHubPullRequestReviewDTO |
| Events | ReviewSubmittedEvent, ReviewApprovedEvent, ReviewChangesRequestedEvent |

### Priority 4: Comments (Issue + PR)

| Component | Target |
|-----------|--------|
| DTO | GitHubIssueCommentDTO, GitHubPullRequestReviewCommentDTO |
| Events | CommentCreatedEvent, CommentUpdatedEvent |

### Priority 5: Users

| Component | Target |
|-----------|--------|
| DTO | GitHubUserDTO (shared across all entities) |
| Processor | GitHubUserProcessor (upsert user from any source) |

### Priority 6: Repositories

| Component | Target |
|-----------|--------|
| DTO | GitHubRepositoryDTO |
| Events | RepositoryAddedEvent, RepositoryRemovedEvent |

### Priority 7: Teams & Organizations

| Component | Target |
|-----------|--------|
| DTO | GitHubTeamDTO, GitHubOrganizationDTO |
| Events | TeamMemberAddedEvent, TeamMemberRemovedEvent |

### Priority 8: Sub-Issues & Dependencies

| Component | Target |
|-----------|--------|
| DTO | GitHubSubIssueDTO, GitHubIssueDependencyDTO |
| Events | SubIssueAddedEvent, DependencyAddedEvent |

---

## DTO Specifications

### Core Issue DTO

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubIssueDTO(
    @JsonProperty("id") Long id,                           // REST: numeric ID, GraphQL: node ID
    @JsonProperty("database_id") Long databaseId,          // GraphQL: databaseId
    @JsonProperty("node_id") String nodeId,                // REST: node_id
    @JsonProperty("number") int number,
    @JsonProperty("title") String title,
    @JsonProperty("body") String body,
    @JsonProperty("state") String state,                   // OPEN, CLOSED
    @JsonProperty("state_reason") String stateReason,      // completed, not_planned, reopened
    @JsonProperty("html_url") String htmlUrl,
    @JsonProperty("comments") int commentsCount,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt,
    @JsonProperty("closed_at") Instant closedAt,
    @JsonProperty("user") GitHubUserRefDTO author,
    @JsonProperty("assignees") List<GitHubUserRefDTO> assignees,
    @JsonProperty("labels") List<GitHubLabelRefDTO> labels,
    @JsonProperty("milestone") GitHubMilestoneRefDTO milestone,
    @JsonProperty("type") GitHubIssueTypeRefDTO issueType,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository
) {
    public Long getDatabaseId() {
        return databaseId != null ? databaseId : id;
    }
}
```

### Webhook Event DTO

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubIssueEventDTO(
    @JsonProperty("action") String action,                 // opened, closed, reopened, edited, labeled, etc.
    @JsonProperty("issue") GitHubIssueDTO issue,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("sender") GitHubUserRefDTO sender,
    @JsonProperty("label") GitHubLabelRefDTO label,        // for labeled/unlabeled actions
    @JsonProperty("type") GitHubIssueTypeRefDTO issueType, // for typed/untyped actions
    @JsonProperty("changes") Map<String, Object> changes   // for edited action
) {
    public boolean isTypedAction() {
        return "typed".equals(action);
    }
    
    public boolean isUntypedAction() {
        return "untyped".equals(action);
    }
}
```

---

## GraphQL Queries

### Issues Query

```graphql
query GetRepositoryIssues($owner: String!, $name: String!, $first: Int!, $after: String, $states: [IssueState!]) {
    repository(owner: $owner, name: $name) {
        issues(first: $first, after: $after, states: $states, orderBy: { field: UPDATED_AT, direction: DESC }) {
            pageInfo {
                hasNextPage
                endCursor
            }
            nodes {
                id
                databaseId
                number
                title
                body
                state
                stateReason
                url
                createdAt
                updatedAt
                closedAt
                
                issueType {
                    id
                    name
                    description
                    color
                    isEnabled
                }
                
                author {
                    ... on User {
                        id
                        databaseId
                        login
                        avatarUrl
                        name
                    }
                }
                
                assignees(first: 20) {
                    nodes {
                        id
                        databaseId
                        login
                        avatarUrl
                    }
                }
                
                labels(first: 50) {
                    nodes {
                        id
                        name
                        color
                    }
                }
                
                milestone {
                    id
                    number
                    title
                    dueOn
                }
            }
        }
    }
}
```

### Pull Requests Query (to be created)

```graphql
query GetRepositoryPullRequests($owner: String!, $name: String!, $first: Int!, $after: String, $states: [PullRequestState!]) {
    repository(owner: $owner, name: $name) {
        pullRequests(first: $first, after: $after, states: $states, orderBy: { field: UPDATED_AT, direction: DESC }) {
            pageInfo {
                hasNextPage
                endCursor
            }
            nodes {
                id
                databaseId
                number
                title
                body
                state
                isDraft
                merged
                mergedAt
                url
                additions
                deletions
                changedFiles
                createdAt
                updatedAt
                closedAt
                
                author {
                    ... on User { id databaseId login avatarUrl name }
                }
                
                mergedBy {
                    ... on User { id databaseId login avatarUrl }
                }
                
                assignees(first: 20) { nodes { id databaseId login avatarUrl } }
                labels(first: 50) { nodes { id name color } }
                milestone { id number title dueOn }
                reviewRequests(first: 20) {
                    nodes {
                        requestedReviewer {
                            ... on User { id databaseId login }
                            ... on Team { id databaseId name slug }
                        }
                    }
                }
            }
        }
    }
}
```

---

## Domain Events

### Event Design Principles

1. **Immutable**: Events are records with final fields
2. **Self-Contained**: Include entity reference and relevant context
3. **Specific**: One event per meaningful domain occurrence
4. **Timing Aware**: Use `@TransactionalEventListener(phase = AFTER_COMMIT)` for async

### Issue Events

```java
public record IssueCreatedEvent(Issue issue, String repositoryFullName) {}
public record IssueUpdatedEvent(Issue issue, Set<String> changedFields) {}
public record IssueClosedEvent(Issue issue, String reason) {}  // completed, not_planned
public record IssueReopenedEvent(Issue issue) {}
public record IssueTypedEvent(Issue issue, IssueType issueType) {}
public record IssueUntypedEvent(Issue issue, IssueType previousType) {}
public record IssueLabeledEvent(Issue issue, Label label) {}
public record IssueUnlabeledEvent(Issue issue, Label label) {}
```

### Pull Request Events

```java
public record PullRequestCreatedEvent(PullRequest pr) {}
public record PullRequestUpdatedEvent(PullRequest pr, Set<String> changedFields) {}
public record PullRequestMergedEvent(PullRequest pr, User mergedBy) {}
public record PullRequestClosedEvent(PullRequest pr) {}
public record PullRequestReopenedEvent(PullRequest pr) {}
public record PullRequestDraftChangedEvent(PullRequest pr, boolean isDraft) {}
public record PullRequestLabelChangedEvent(PullRequest pr, Label label, boolean added) {}
public record PullRequestReviewRequestedEvent(PullRequest pr, User reviewer) {}
public record PullRequestReviewRequestRemovedEvent(PullRequest pr, User reviewer) {}
```

### Review Events

```java
public record ReviewSubmittedEvent(PullRequestReview review) {}
public record ReviewApprovedEvent(PullRequestReview review) {}
public record ReviewChangesRequestedEvent(PullRequestReview review) {}
public record ReviewDismissedEvent(PullRequestReview review) {}
```

---

## Implementation Strategy

### Phase 1: Issues (This PR)

**Files to Create:**
1. `gitprovider/issue/github/dto/GitHubIssueDTO.java` ✅
2. `gitprovider/issue/github/dto/GitHubIssueEventDTO.java`
3. `gitprovider/issue/github/dto/GitHubUserRefDTO.java`
4. `gitprovider/issue/github/dto/GitHubLabelRefDTO.java`
5. `gitprovider/issue/github/dto/GitHubMilestoneRefDTO.java`
6. `gitprovider/issue/github/dto/GitHubIssueTypeRefDTO.java`
7. `gitprovider/issue/github/GitHubIssueFetcher.java`
8. `gitprovider/issue/github/GitHubIssueParser.java`
9. `gitprovider/issue/github/GitHubIssueProcessor.java`
10. `gitprovider/issue/events/IssueCreatedEvent.java`
11. `gitprovider/issue/events/IssueUpdatedEvent.java`
12. `gitprovider/issue/events/IssueTypedEvent.java`
13. `graphql/github/operations/GetRepositoryIssues.graphql` ✅

**Files to Modify:**
1. `gitprovider/issue/github/GitHubIssueSyncService.java` - Use Fetcher + Processor
2. `gitprovider/issue/github/GitHubIssueMessageHandler.java` - Use Parser + Processor

**Files to Delete:**
1. `gitprovider/issue/github/GitHubIssueConverter.java` - Logic moves to Processor
2. `org/kohsuke/github/GHIssueWithType.java` - No longer needed
3. `org/kohsuke/github/GHEventPayloadIssueWithType.java` - No longer needed

### Phase 2: Pull Requests

Same pattern as Issues. Special attention to:
- Label change events for lifecycle detection
- Merge events for contribution tracking
- Review request events for workflow

### Phase 3: Reviews & Comments

Dependent on PRs being complete.

### Phase 4: Users, Repos, Teams

Foundation entities used by all others.

### Phase 5: Cleanup

1. Remove all hub4j imports from migrated files
2. Remove `GitHubApiPatches.java` (ByteBuddy workarounds)
3. Remove `org.kohsuke.github` package extensions
4. Update tests to use DTOs

---

## Testing Strategy

### Unit Tests

```java
@Test
void processorCreatesEntity() {
    var dto = new GitHubIssueDTO(
        123L, 123L, "node123", 42, "Test Issue", "Body",
        "OPEN", null, "https://...", 0,
        Instant.now(), Instant.now(), null,
        new GitHubUserRefDTO(1L, "login", "avatar"),
        List.of(), List.of(), null, null, null
    );
    
    Issue result = processor.process(dto, "owner/repo");
    
    assertThat(result.getNumber()).isEqualTo(42);
    assertThat(result.getTitle()).isEqualTo("Test Issue");
}
```

### Integration Tests

```java
@Test
void syncFetchesAndProcessesIssues() {
    // GraphQL mock returns issues
    // Verify processor called for each
    // Verify events published
}
```

### Event Tests

```java
@Test
void processorPublishesEventOnCreate() {
    // Process new issue
    // Verify IssueCreatedEvent published
}
```

---

## Configuration

### application.yml

```yaml
github:
  graphql:
    page-size: 100
    timeout-seconds: 30
  webhooks:
    signature-verification: true
```

---

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| GraphQL rate limits | Use efficient queries, implement backoff |
| Missing GraphQL fields | Verify all needed fields available in schema |
| Event storm on large sync | Batch events, use async listeners |
| Breaking existing features | Comprehensive test coverage before refactoring |

---

## Success Criteria

1. **Zero hub4j imports** in migrated entity packages
2. **All tests pass** with new architecture
3. **Events published** for all entity lifecycle changes
4. **BadPracticeDetector** reacts to events instead of polling
5. **Issue types sync** correctly from both GraphQL and webhooks

---

## Timeline Estimate

| Phase | Effort | Dependencies |
|-------|--------|--------------|
| Issues | 2-3 days | None |
| Pull Requests | 2-3 days | Issues complete |
| Reviews | 1-2 days | PRs complete |
| Comments | 1-2 days | Issues, PRs complete |
| Users/Repos/Teams | 2-3 days | None |
| Cleanup & Testing | 2-3 days | All phases |

**Total: ~2-3 weeks for complete refactoring**

---

## Appendix: Files Using hub4j

<details>
<summary>Full list of 67 files with hub4j imports</summary>

Run this command to regenerate:
```bash
grep -r "org.kohsuke.github" src/main/java --include="*.java" -l
```

Key files per entity:
- Issues: GitHubIssueSyncService, GitHubIssueConverter, GitHubIssueMessageHandler
- PRs: GitHubPullRequestSyncService, GitHubPullRequestMessageHandler
- Reviews: GitHubPullRequestReviewSyncService, GitHubPullRequestReviewMessageHandler
- Comments: GitHubIssueCommentSyncService, GitHubPullRequestReviewCommentSyncService
- Users: GitHubUserSyncService, GitHubUserConverter
- Teams: GitHubTeamSyncService, GitHubTeamConverter
- Repositories: GitHubRepositorySyncService
- Common: GitHubClientProvider, GitHubMessageHandler, GitHubClientExecutor

</details>
