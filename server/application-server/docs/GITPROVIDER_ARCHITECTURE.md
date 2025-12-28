# GitProvider Module Architecture

## Vision

The `gitprovider` module is the **ETL (Extract-Transform-Load) engine** for GitHub data.

```
┌─────────────────────────────────────────────────────────────────────┐
│                         GITPROVIDER MODULE                          │
│                     (Pure ETL Infrastructure)                       │
│                                                                     │
│  ┌─────────────┐    ┌──────────────┐    ┌─────────────────────────┐│
│  │   EXTRACT   │    │  TRANSFORM   │    │          LOAD           ││
│  │             │    │              │    │                         ││
│  │ • GraphQL   │───▶│ • Processors │───▶│ • JPA Entities          ││
│  │ • Webhooks  │    │ • Converters │    │ • Repositories          ││
│  │ • NATS      │    │ • DTOs       │    │ • Domain Events         ││
│  └─────────────┘    └──────────────┘    └─────────────────────────┘│
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │                      ORCHESTRATION                              ││
│  │  • Sync Scheduler    • Sync Services    • Message Handlers      ││
│  └─────────────────────────────────────────────────────────────────┘│
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │                    SPI (Service Provider Interface)             ││
│  │  gitprovider DEFINES these interfaces                           ││
│  │  Other modules IMPLEMENT them                                   ││
│  │                                                                 ││
│  │  • InstallationTokenProvider (→ get installation ID)            ││
│  │  • WorkspaceContextResolver  (→ map org → workspace)            ││
│  │  • SyncTargetProvider        (→ which repos to sync) [NEW]      ││
│  └─────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────┘
                              │
                              │ domain events
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        OTHER MODULES                                │
│                                                                     │
│  workspace ──────▶ IMPLEMENTS gitprovider SPIs                      │
│  activity  ──────▶ LISTENS to domain events                         │
│  leaderboard ───▶ READS from gitprovider entities                   │
│  contributors ──▶ READS from gitprovider entities                   │
└─────────────────────────────────────────────────────────────────────┘
```

## Core Principles

### 1. gitprovider Does NOT Import From Other Modules

**WRONG:**
```java
// In gitprovider - VIOLATION!
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
```

**RIGHT:**
```java
// In gitprovider - defines interface
public interface SyncTargetProvider {
    List<SyncTarget> getActiveSyncTargets();
}

// In workspace - implements interface
@Component
public class WorkspaceSyncTargetProvider implements SyncTargetProvider {
    private final WorkspaceRepository workspaceRepository;
    // ...
}
```

### 2. Other Modules CAN Import From gitprovider

```java
// In workspace - OK!
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
```

### 3. Cross-Module Communication Via Domain Events

gitprovider publishes events, other modules consume them:

```java
// In gitprovider (publisher)
publisher.publishEvent(new DomainEvent.PullRequestCreated(...));

// In activity module (consumer)
@TransactionalEventListener
public void onPullRequestCreated(DomainEvent.PullRequestCreated event) {
    badPracticeDetector.detect(event.pullRequest().id());
}
```

---

## What STAYS in gitprovider (ETL Engine)

Everything related to GitHub data extraction, transformation, and loading:

### Entities (The "L" - Load target)
- `User`, `Organization`, `Repository`
- `Issue`, `PullRequest`, `IssueComment`
- `Label`, `Milestone`, `IssueType`
- `PullRequestReview`, `PullRequestReviewComment`, `PullRequestReviewThread`
- `Team`, `TeamMembership`, `RepositoryCollaborator`

### Processors (The "T" - Transform)
- `GitHubIssueProcessor`, `GitHubPullRequestProcessor`
- `GitHubLabelProcessor`, `GitHubMilestoneProcessor`
- `GitHubIssueCommentProcessor`, `GitHubTeamProcessor`
- etc.

### Sync Services (The "E" - Extract)
- `GitHubIssueSyncService`, `GitHubPullRequestSyncService`
- `GitHubLabelSyncService`, `GitHubMilestoneSyncService`
- `GitHubRepositorySyncService`, `GitHubOrganizationSyncService`
- `GitHubDataSyncService` (orchestrates full sync)
- `GitHubDataSyncScheduler` (triggers scheduled syncs)

### Message Handlers (Webhook ingestion)
- `GitHubIssueMessageHandler`, `GitHubPullRequestMessageHandler`
- `GitHubInstallationMessageHandler` (extracts installation data)
- All webhook event handlers

### Infrastructure
- `GitHubGraphQlClientProvider` (API client)
- `GitHubAppTokenService` (authentication)
- `ProcessingContext`, `ProcessingContextFactory`
- `NatsConsumerService` (NATS subscription management)

---

## What gitprovider Needs From Outside (SPIs)

These interfaces are DEFINED in gitprovider and IMPLEMENTED by other modules:

### 1. InstallationTokenProvider (EXISTING)
```java
// In gitprovider.common.spi
public interface InstallationTokenProvider {
    Optional<Long> getInstallationId(Long workspaceId);
    Optional<String> getPersonalAccessToken(Long workspaceId);
    AuthMode getAuthMode(Long workspaceId);
}
```

### 2. WorkspaceContextResolver (EXISTING)
```java
// In gitprovider.common.spi
public interface WorkspaceContextResolver {
    Optional<Long> findWorkspaceIdByOrgLogin(String orgLogin);
}
```

### 3. SyncTargetProvider (NEW - NEEDED)
```java
// In gitprovider.common.spi
public interface SyncTargetProvider {
    /**
     * Get all active sync targets (repos to sync).
     * Returns primitives/records, not JPA entities.
     */
    List<SyncTarget> getActiveSyncTargets();
    
    /**
     * Update sync timestamp after successful sync.
     */
    void updateSyncTimestamp(Long workspaceId, String repoNameWithOwner, SyncType type, Instant syncedAt);
}

public record SyncTarget(
    Long workspaceId,
    Long installationId,
    String personalAccessToken,
    AuthMode authMode,
    String repositoryNameWithOwner,
    Instant lastLabelsSyncedAt,
    Instant lastMilestonesSyncedAt,
    Instant lastIssuesSyncedAt
) {}
```

### 4. WorkspaceProvisioningListener (NEW - for installation events)
```java
// In gitprovider.common.spi
public interface WorkspaceProvisioningListener {
    /**
     * Called when a GitHub App installation is created.
     * Workspace module should provision a new workspace.
     */
    void onInstallationCreated(InstallationData installation);
    
    /**
     * Called when a GitHub App installation is deleted.
     */
    void onInstallationDeleted(Long installationId);
    
    /**
     * Called when org/account is renamed.
     */
    void onAccountRenamed(Long installationId, String oldLogin, String newLogin);
}

public record InstallationData(
    Long installationId,
    String accountLogin,
    AccountType accountType,
    List<String> repositoryNames
) {}
```

---

## Refactoring Plan

### Phase 1: Create Missing SPIs ✅ PARTIALLY DONE

SPIs already created:
- `InstallationTokenProvider` ✅
- `WorkspaceContextResolver` ✅

SPIs to create:
- `SyncTargetProvider` ❌
- `WorkspaceProvisioningListener` ❌

### Phase 2: Refactor Services to Use SPIs

**GitHubDataSyncScheduler:**
```java
// BEFORE (imports workspace)
private final WorkspaceService workspaceService;
for (Workspace workspace : workspaceService.getActiveWorkspaces()) {
    syncService.syncForWorkspace(workspace.getId());
}

// AFTER (uses SPI)
private final SyncTargetProvider syncTargetProvider;
for (SyncTarget target : syncTargetProvider.getActiveSyncTargets()) {
    syncService.syncForTarget(target);
}
```

**GitHubDataSyncService:**
```java
// BEFORE (imports workspace entities)
private final WorkspaceRepository workspaceRepository;
private final RepositoryToMonitorRepository repositoryToMonitorRepository;

// AFTER (uses SPI)
private final SyncTargetProvider syncTargetProvider;
```

**GitHubInstallationMessageHandler:**
```java
// BEFORE (calls WorkspaceService directly)
workspaceService.ensureForInstallation(payload);

// AFTER (publishes event or calls SPI)
workspaceProvisioningListener.onInstallationCreated(installationData);
```

### Phase 3: Remove Organization ↔ Workspace Bidirectional Reference

**Organization.java:**
```java
// REMOVE this field
@OneToOne(mappedBy = "organization")
private Workspace workspace;
```

**Workspace can still reference Organization:**
```java
// This is OK - workspace depends on gitprovider
@OneToOne
private Organization organization;
```

### Phase 4: Move Controllers Out of gitprovider

Controllers are UI/API concerns, not ETL:

```
de.tum.in.www1.hephaestus.workspace.team/
├── TeamController.java  ← MOVE from gitprovider.team
```

### Phase 5: Remove PullRequestReviewInfoDTOConverter Leaderboard Dependency

**Option A:** Move converter to leaderboard module
**Option B:** Compute score in service layer, not converter

---

## Violation Categories

Current 476 violations fall into these categories:

| Category | Count (est.) | Fix Strategy |
|----------|-------------|--------------|
| Sync services → WorkspaceRepository | ~50 | Use SyncTargetProvider SPI |
| Sync services → RepositoryToMonitorRepository | ~30 | Use SyncTargetProvider SPI |
| Installation handlers → WorkspaceService | ~20 | Use WorkspaceProvisioningListener SPI |
| Organization ↔ Workspace bidirectional | ~50 | Remove from Organization |
| TeamController → @WorkspaceScopedController | ~10 | Move controller out |
| GraphQL client → WorkspaceRepository | ~20 | Use InstallationTokenProvider |
| Converter → ScoringService | ~10 | Move converter |
| ProcessingContextFactory → WorkspaceRepository | ~10 | Use WorkspaceContextResolver |
| **Tests** (legitimate for fixtures) | ~270 | Accept or use test utilities |

---

## Test Strategy

Integration tests in gitprovider package legitimately need workspace fixtures:
- They test end-to-end flows
- They need real database state

**Options:**
1. **Accept it:** Test code can have broader dependencies
2. **Test utilities module:** Create shared test fixtures
3. **Mock SPIs:** Tests inject mock implementations

---

## Success Criteria

When complete:
1. `ArchitectureTest#gitProviderModuleShouldNotDependOnOtherModules` passes (or only test violations remain)
2. gitprovider has ZERO imports from `..workspace..`, `..activity..`, `..leaderboard..`
3. All sync/ETL functionality remains IN gitprovider
4. Other modules implement SPIs to provide data gitprovider needs
