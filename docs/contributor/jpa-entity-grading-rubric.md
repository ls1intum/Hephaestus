# JPA Entity Schema Quality Grading Rubric

> **Philosophy**: This rubric exists because mediocre schemas become technical debt that compounds forever. Most first-pass schemas should grade **B or C**. An **A+** is rare and earned—it means the schema could be open-sourced as a reference implementation.

---

## Quick Reference: Grade Thresholds

| Grade | Score | Meaning |
|-------|-------|---------|
| **A+** | 97-100 | Production-ready, exemplary, reference quality |
| **A** | 93-96 | Excellent, minor polish needed |
| **A-** | 90-92 | Very good, few issues |
| **B+** | 87-89 | Good but notable gaps |
| **B** | 83-86 | Acceptable, needs work |
| **B-** | 80-82 | Below standard |
| **C+** | 77-79 | Significant issues |
| **C** | 73-76 | Major gaps |
| **C-** | 70-72 | Barely functional |
| **D** | 60-69 | Unacceptable for production |
| **F** | <60 | Requires complete rework |

---

## Dimension 1: Field Coverage (Weight: 25%)

### What We're Measuring
Does the entity capture all essential fields from the source system (GitHub/GitLab GraphQL API) that a developer integrator platform would need?

### GitHub GraphQL Reference Fields (Examples)

#### User Entity - Essential Fields
| Field | GitHub Type | Priority | Notes |
|-------|-------------|----------|-------|
| `id` (Node ID) | `ID!` | Critical | Global unique identifier |
| `databaseId` | `Int` | Critical | REST API compatibility |
| `login` | `String!` | Critical | Username |
| `name` | `String` | High | Display name |
| `email` | `String!` | High | Public email |
| `avatarUrl` | `URI!` | High | Profile picture |
| `bio` | `String` | Medium | Profile bio |
| `company` | `String` | Medium | Employer |
| `location` | `String` | Medium | Geographic location |
| `websiteUrl` | `URI` | Medium | Personal website |
| `twitterUsername` | `String` | Low | Social link |
| `createdAt` | `DateTime!` | High | Account creation |
| `updatedAt` | `DateTime!` | High | Last modification |
| `isHireable` | `Boolean!` | Low | Job seeking status |
| `isSiteAdmin` | `Boolean!` | Medium | GitHub staff |

#### Repository Entity - Essential Fields
| Field | GitHub Type | Priority | Notes |
|-------|-------------|----------|-------|
| `id` (Node ID) | `ID!` | Critical | Global unique identifier |
| `databaseId` | `Int` | Critical | REST API compatibility |
| `name` | `String!` | Critical | Repo name |
| `nameWithOwner` | `String!` | Critical | Full path (owner/repo) |
| `description` | `String` | High | Repo description |
| `url` | `URI!` | Critical | Web URL |
| `homepageUrl` | `URI` | Medium | Project website |
| `createdAt` | `DateTime!` | High | Creation timestamp |
| `updatedAt` | `DateTime!` | High | Last update |
| `pushedAt` | `DateTime` | High | Last push |
| `isPrivate` | `Boolean!` | Critical | Visibility |
| `isArchived` | `Boolean!` | High | Archive status |
| `isDisabled` | `Boolean!` | Medium | Disabled status |
| `isFork` | `Boolean!` | High | Fork indicator |
| `isTemplate` | `Boolean!` | Medium | Template repo |
| `forkCount` | `Int!` | Medium | Fork count |
| `stargazerCount` | `Int!` | Medium | Star count |
| `visibility` | `RepositoryVisibility!` | High | PUBLIC/PRIVATE/INTERNAL |
| `defaultBranchRef.name` | `String` | High | Default branch name |
| `primaryLanguage` | `Language` | Medium | Main language |
| `licenseInfo` | `License` | Medium | License type |

#### Issue Entity - Essential Fields
| Field | GitHub Type | Priority | Notes |
|-------|-------------|----------|-------|
| `id` (Node ID) | `ID!` | Critical | Global unique identifier |
| `databaseId` | `Int` | Critical | REST API compatibility |
| `number` | `Int!` | Critical | Issue number |
| `title` | `String!` | Critical | Issue title |
| `body` | `String!` | Critical | Issue body (Markdown) |
| `bodyHTML` | `HTML!` | Medium | Rendered HTML |
| `state` | `IssueState!` | Critical | OPEN/CLOSED |
| `stateReason` | `IssueStateReason` | High | COMPLETED/NOT_PLANNED/REOPENED |
| `author` | `Actor` | Critical | Who created it |
| `assignees` | `UserConnection!` | High | Assigned users |
| `labels` | `LabelConnection` | High | Applied labels |
| `milestone` | `Milestone` | Medium | Associated milestone |
| `createdAt` | `DateTime!` | Critical | Creation time |
| `updatedAt` | `DateTime!` | Critical | Last update |
| `closedAt` | `DateTime` | High | When closed |
| `locked` | `Boolean!` | Medium | Conversation locked |
| `activeLockReason` | `LockReason` | Low | Why locked |
| `url` | `URI!` | Critical | Web URL |
| `repository` | `Repository!` | Critical | Parent repository |

#### PullRequest Entity - Essential Fields
| Field | GitHub Type | Priority | Notes |
|-------|-------------|----------|-------|
| `id` (Node ID) | `ID!` | Critical | Global unique identifier |
| `databaseId` | `Int` | Critical | REST API compatibility |
| `number` | `Int!` | Critical | PR number |
| `title` | `String!` | Critical | PR title |
| `body` | `String!` | Critical | PR body |
| `state` | `PullRequestState!` | Critical | OPEN/CLOSED/MERGED |
| `author` | `Actor` | Critical | Who created it |
| `baseRefName` | `String!` | Critical | Target branch name |
| `headRefName` | `String!` | Critical | Source branch name |
| `baseRefOid` | `GitObjectID!` | High | Target commit SHA |
| `headRefOid` | `GitObjectID!` | High | Source commit SHA |
| `isDraft` | `Boolean!` | Critical | Draft status |
| `merged` | `Boolean!` | Critical | Merge status |
| `mergedAt` | `DateTime` | High | When merged |
| `mergedBy` | `Actor` | High | Who merged |
| `closedAt` | `DateTime` | High | When closed |
| `createdAt` | `DateTime!` | Critical | Creation time |
| `updatedAt` | `DateTime!` | Critical | Last update |
| `additions` | `Int!` | High | Lines added |
| `deletions` | `Int!` | High | Lines deleted |
| `changedFiles` | `Int!` | High | Files changed |
| `mergeable` | `MergeableState!` | Medium | Can be merged |
| `reviewDecision` | `PullRequestReviewDecision` | High | Review status |
| `url` | `URI!` | Critical | Web URL |
| `repository` | `Repository!` | Critical | Parent repository |

### Scoring Matrix

| Score | Coverage | Description |
|-------|----------|-------------|
| **100** | 98%+ | All critical + high + most medium fields captured |
| **95** | 95-97% | All critical + high fields, some medium missing |
| **90** | 90-94% | All critical fields, most high fields |
| **85** | 85-89% | All critical, some high missing |
| **80** | 80-84% | Most critical, some missing |
| **70** | 70-79% | Missing critical fields |
| **60** | 60-69% | Missing multiple critical fields |
| **50** | 50-59% | Half of essential fields missing |
| **40** | 40-49% | Majority missing |
| **0-39** | <40% | Fundamental rework needed |

### Red Flags (Automatic Deductions)
- Missing `id`/`databaseId`: -20 points
- Missing `createdAt`/`updatedAt`: -15 points
- Missing `url`/`htmlUrl`: -10 points
- Missing state/status fields: -15 points
- No relationship to parent entity (e.g., PR without repository): -20 points

---

## Dimension 2: Type Correctness (Weight: 20%)

### What We're Measuring
Are column types, nullability, and constraints correct and optimal for the data they store?

### Type Mapping Guidelines

| Source Type | Correct JPA Type | Correct DB Type | Common Mistakes |
|-------------|------------------|-----------------|-----------------|
| `ID!` (Node ID) | `String` | `VARCHAR(255)` or `TEXT` | Using `INTEGER` |
| `Int` | `Long` | `BIGINT` | Using `INT` (overflow risk) |
| `Int!` | `Long` (non-null) | `BIGINT NOT NULL` | Nullable when shouldn't be |
| `String` | `String` | `TEXT` | Using `VARCHAR(255)` for bodies |
| `String!` | `String` (non-null) | `TEXT NOT NULL` | Missing NOT NULL |
| `URI` | `String` | `TEXT` | `VARCHAR` too short for URLs |
| `DateTime` | `Instant`/`OffsetDateTime` | `TIMESTAMPTZ` | Using `TIMESTAMP` without TZ |
| `DateTime!` | `Instant` (non-null) | `TIMESTAMPTZ NOT NULL` | Nullable timestamps |
| `Boolean` | `Boolean` | `BOOLEAN` | Using integers |
| `Boolean!` | `boolean` (primitive) | `BOOLEAN NOT NULL` | Using wrapper when non-null |
| `HTML` | `String` | `TEXT` | Length restrictions |
| `GitObjectID` | `String` | `CHAR(40)` or `VARCHAR(40)` | Wrong length |
| Enums | Java `enum` | `VARCHAR` or PostgreSQL `enum` | Plain strings |

### Nullability Rules
```
Source API says required (!) → JPA field should be non-null → DB column NOT NULL
Source API says optional    → JPA field nullable          → DB column nullable
```

### Scoring Matrix

| Score | Description |
|-------|-------------|
| **100** | Zero type issues, perfect nullability, enums used appropriately |
| **95** | 1-2 minor type issues (e.g., VARCHAR vs TEXT for non-critical field) |
| **90** | 3-5 minor issues OR 1 moderate issue |
| **85** | Multiple moderate issues |
| **80** | Significant type mismatches |
| **70** | Nullable fields that should be NOT NULL, or vice versa |
| **60** | Wrong types that could cause data loss (INT vs BIGINT) |
| **50** | Multiple wrong types causing potential data issues |
| **0-49** | Fundamental type errors |

### Red Flags (Automatic Deductions)
- `INT` instead of `BIGINT` for IDs: -15 points
- `TIMESTAMP` without timezone: -10 points
- `VARCHAR(255)` for body/description fields: -10 points
- Missing `@Lob` or `TEXT` for large text fields: -10 points
- Nullable when source says required: -5 points per field
- Non-null when source says optional: -3 points per field (data loss risk)
- No enum for known value sets (state, visibility): -5 points per field

---

## Dimension 3: Naming Consistency (Weight: 15%)

### What We're Measuring
Do names follow conventions, match source systems appropriately, and communicate intent clearly?

### Naming Convention Rules

#### Java Side (Entity Classes)
```java
// Class names: PascalCase, singular
public class PullRequest { ... }        // ✓
public class PullRequests { ... }       // ✗ (plural)
public class pull_request { ... }       // ✗ (snake_case)

// Field names: camelCase
private String headRefName;             // ✓
private String head_ref_name;           // ✗ (snake_case)
private String HeadRefName;             // ✗ (PascalCase)

// Boolean fields: use "is" prefix when natural
private boolean isDraft;                // ✓
private boolean draft;                  // Acceptable
private boolean draftStatus;            // ✗ (redundant "Status")
```

#### Database Side (Tables/Columns)
```sql
-- Table names: snake_case, singular
CREATE TABLE pull_request ( ... );      -- ✓
CREATE TABLE PullRequest ( ... );       -- ✗ (PascalCase)
CREATE TABLE pull_requests ( ... );     -- ✗ (plural)

-- Column names: snake_case
head_ref_name                           -- ✓
headRefName                             -- ✗ (camelCase)
HEAD_REF_NAME                           -- ✗ (SCREAMING_CASE)

-- Foreign keys: <entity>_id
author_id                               -- ✓
authorId                                -- ✗
author_fk                               -- ✗
fk_author                               -- ✗
```

### Source System Alignment

| GitHub Field | Expected Java | Expected DB Column |
|--------------|---------------|-------------------|
| `headRefName` | `headRefName` | `head_ref_name` |
| `baseRefName` | `baseRefName` | `base_ref_name` |
| `isDraft` | `isDraft` | `is_draft` |
| `mergedAt` | `mergedAt` | `merged_at` |
| `databaseId` | `databaseId` | `database_id` |
| `nameWithOwner` | `nameWithOwner` | `name_with_owner` |

### Anti-Patterns

| Bad Name | Problem | Good Name |
|----------|---------|-----------|
| `prNumber` | Abbreviation | `number` or `pullRequestNumber` |
| `repoId` | Abbreviation | `repositoryId` |
| `usr` | Abbreviation | `user` |
| `created` | Ambiguous | `createdAt` |
| `updated` | Ambiguous | `updatedAt` |
| `status` | Vague (status of what?) | `state` or `reviewStatus` |
| `flag` | Meaningless | Describe what it flags |
| `data` | Meaningless | Describe what data |
| `info` | Meaningless | Be specific |
| `htmlUrl` | GitHub-specific | `url` (generic) |
| `full_name` | Only used by some APIs | `nameWithOwner` |

### Scoring Matrix

| Score | Description |
|-------|-------------|
| **100** | Perfect consistency, clear names, proper alignment with source |
| **95** | 1-2 minor naming inconsistencies |
| **90** | A few inconsistencies, all names still clear |
| **85** | Some legacy names or abbreviations |
| **80** | Multiple inconsistencies but understandable |
| **70** | Confusing names or mixed conventions |
| **60** | Significant naming issues affecting readability |
| **50** | Inconsistent conventions throughout |
| **0-49** | Names actively hinder understanding |

### Red Flags (Automatic Deductions)
- Mixed case conventions in same entity: -10 points
- Abbreviations without explanation: -5 points per occurrence
- Names that don't match source AND aren't documented: -5 points per field
- Reserved words used as names: -10 points
- Single-letter variable names (except loops): -5 points per occurrence

---

## Dimension 4: GitLab Portability (Weight: 15%)

### What We're Measuring
Can this schema support GitLab (and potentially other providers) without major refactoring?

### Provider-Agnostic Field Mapping

| Concept | GitHub Term | GitLab Term | Recommended Field Name |
|---------|-------------|-------------|----------------------|
| Code Review | Pull Request | Merge Request | `pullRequest` (with `type` discriminator) |
| Repository | Repository | Project | `repository` |
| User | User | User | `user` |
| Organization | Organization | Group | `organization` or `account` |
| Issue | Issue | Issue | `issue` |
| PR State | `OPEN/CLOSED/MERGED` | `opened/closed/merged` | Normalize to enum |
| Visibility | `PUBLIC/PRIVATE/INTERNAL` | `public/private/internal` | Enum works |
| Node ID | `id` (base64 encoded) | `id` (integer) | Store both or normalize |
| REST ID | `databaseId` | `id` | `platformId` or separate columns |

### Required Provider Discriminator Fields

```java
// Option 1: Discriminator column (recommended for single-table inheritance)
@Column(name = "git_provider")
@Enumerated(EnumType.STRING)
private GitProvider gitProvider;  // GITHUB, GITLAB, BITBUCKET

// Option 2: Separate ID columns
private String githubNodeId;      // GitHub's base64 node ID
private Long gitlabId;            // GitLab's integer ID

// Option 3: Generic provider ID (less type-safe but more flexible)
private String providerId;        // Platform-specific identifier
```

### Cross-Platform Considerations

#### Fields That Differ Significantly
| Field | GitHub | GitLab | Solution |
|-------|--------|--------|----------|
| PR Labels | Labels on PR | Labels on MR | Generic labels relation |
| Approvals | Review with APPROVED | Approval rules | Abstract approval concept |
| CI Status | Commit status checks | Pipeline status | Separate CI status table |
| Draft | `isDraft` boolean | `work_in_progress` | Normalize to `isDraft` |
| Merge Method | `MERGE/SQUASH/REBASE` | Similar | Enum covers both |

#### Fields Unique to One Platform
```java
// Document platform-specific fields clearly
/**
 * GitHub-specific: Whether this PR is in a merge queue.
 * Not applicable for GitLab (returns null).
 */
private Boolean isInMergeQueue;

/**
 * GitLab-specific: The merge request approval state.
 * Not applicable for GitHub (returns null).
 */
private String approvalState;
```

### Scoring Matrix

| Score | Description |
|-------|-------------|
| **100** | Fully provider-agnostic, GitLab works with zero changes |
| **95** | Minor platform-specific fields, well-documented |
| **90** | Some platform-specific fields, clear abstraction layer |
| **85** | Good abstraction but some GitHub-specific naming |
| **80** | Needs minor refactoring for GitLab |
| **70** | Significant GitHub assumptions in schema |
| **60** | Would require moderate refactoring |
| **50** | Deep GitHub coupling |
| **0-49** | Complete rework needed for GitLab |

### Red Flags (Automatic Deductions)
- No provider discriminator field where needed: -15 points
- GitHub-specific field names without abstraction: -5 points per field
- No handling for different ID formats: -10 points
- Hardcoded GitHub-only enums: -10 points
- No documentation of platform-specific fields: -5 points

---

## Dimension 5: Index Strategy (Weight: 10%)

### What We're Measuring
Are indexes present for critical query paths? Is there over-indexing or missing indexes?

### Required Indexes by Entity

#### User Entity
```sql
-- Critical (must have)
CREATE INDEX idx_user_login ON "user"(login);
CREATE UNIQUE INDEX idx_user_database_id ON "user"(database_id);

-- Important
CREATE INDEX idx_user_email ON "user"(email) WHERE email IS NOT NULL;
CREATE INDEX idx_user_type ON "user"(type);  -- USER, BOT, ORGANIZATION
```

#### Repository Entity
```sql
-- Critical
CREATE UNIQUE INDEX idx_repository_name_with_owner ON repository(name_with_owner);
CREATE INDEX idx_repository_owner_id ON repository(owner_id);

-- Important
CREATE INDEX idx_repository_visibility ON repository(visibility);
CREATE INDEX idx_repository_is_archived ON repository(is_archived);
CREATE INDEX idx_repository_created_at ON repository(created_at);
```

#### Issue/PullRequest Entity
```sql
-- Critical
CREATE UNIQUE INDEX idx_issue_repo_number ON issue(repository_id, number);
CREATE INDEX idx_issue_author_id ON issue(author_id);
CREATE INDEX idx_issue_state ON issue(state);
CREATE INDEX idx_issue_created_at ON issue(created_at);

-- Important
CREATE INDEX idx_issue_updated_at ON issue(updated_at);
CREATE INDEX idx_issue_closed_at ON issue(closed_at) WHERE closed_at IS NOT NULL;

-- For PR-specific queries
CREATE INDEX idx_pr_merged_at ON pull_request(merged_at) WHERE merged_at IS NOT NULL;
CREATE INDEX idx_pr_is_draft ON pull_request(is_draft);
```

#### Composite Indexes for Common Queries
```sql
-- "All open PRs in a repo, ordered by created"
CREATE INDEX idx_pr_repo_state_created 
  ON pull_request(repository_id, state, created_at DESC);

-- "User's recent activity"
CREATE INDEX idx_issue_author_created 
  ON issue(author_id, created_at DESC);

-- "PRs by branch"
CREATE INDEX idx_pr_repo_base_ref 
  ON pull_request(repository_id, base_ref_name);
```

### Index Anti-Patterns

| Anti-Pattern | Problem | Fix |
|--------------|---------|-----|
| Indexing every column | Write performance degradation | Index only queried columns |
| Indexing low-cardinality columns alone | Index not selective | Combine with high-cardinality columns |
| No index on FK columns | Slow joins and cascade deletes | Add index on all FK columns |
| Redundant indexes | `(a)` + `(a, b)` → `(a)` is redundant | Remove redundant |
| No partial indexes | Full index when NULL frequent | Use `WHERE` clause |

### Scoring Matrix

| Score | Description |
|-------|-------------|
| **100** | All critical paths indexed, no redundancy, partial indexes used appropriately |
| **95** | All critical indexes, minor optimization opportunities |
| **90** | Good coverage, 1-2 missing important indexes |
| **85** | Most indexes present, some composite indexes missing |
| **80** | Basic indexes present, missing composite indexes |
| **70** | Missing indexes on FKs or common query paths |
| **60** | Significant missing indexes |
| **50** | Only PK indexes |
| **0-49** | No indexing strategy |

### Red Flags (Automatic Deductions)
- No index on FK column: -5 points per FK
- No index on `login`/`email`/`name_with_owner`: -10 points
- Missing composite index for obvious query patterns: -5 points per pattern
- Over-indexing (>10 indexes on simple entity): -5 points
- Duplicate indexes: -5 points per duplicate

---

## Dimension 6: Data Integrity (Weight: 10%)

### What We're Measuring
Are referential integrity constraints in place and correct? Do cascade behaviors make sense?

### Required Constraints

#### Foreign Key Constraints
```java
// Every relationship MUST have FK constraint
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "repository_id", nullable = false, 
            foreignKey = @ForeignKey(name = "fk_issue_repository"))
private Repository repository;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "author_id",
            foreignKey = @ForeignKey(name = "fk_issue_author"))
private User author;  // Nullable - can be deleted user
```

#### Cascade Behavior Guidelines

| Parent Entity | Child Entity | Recommended Cascade | Reasoning |
|---------------|--------------|---------------------|-----------|
| Repository | Issue | `REMOVE` | Issues belong to repo |
| Repository | PullRequest | `REMOVE` | PRs belong to repo |
| Issue | IssueComment | `REMOVE` | Comments belong to issue |
| PullRequest | PRReview | `REMOVE` | Reviews belong to PR |
| User | Issue | `SET NULL` | Keep issues if user deleted |
| User | PullRequest | `SET NULL` | Keep PRs if user deleted |
| Repository | Repository (fork) | `SET NULL` | Keep forks if parent deleted |

#### Constraint Examples
```java
// GOOD: Proper orphan removal
@OneToMany(mappedBy = "issue", cascade = CascadeType.ALL, orphanRemoval = true)
private List<IssueComment> comments;

// GOOD: Nullable FK for optional relationships
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "merged_by_id")  // Nullable - not merged yet
private User mergedBy;

// BAD: CASCADE ALL on entity that should persist independently
@ManyToOne(cascade = CascadeType.ALL)  // ✗ Deleting PR deletes User!
private User author;
```

### Unique Constraints

```java
// Natural keys should have unique constraints
@Table(name = "repository", uniqueConstraints = {
    @UniqueConstraint(name = "uk_repository_name_with_owner", 
                      columnNames = {"name_with_owner"})
})

@Table(name = "issue", uniqueConstraints = {
    @UniqueConstraint(name = "uk_issue_repo_number", 
                      columnNames = {"repository_id", "number"})
})

@Table(name = "user", uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_login", columnNames = {"login"}),
    @UniqueConstraint(name = "uk_user_database_id", columnNames = {"database_id"})
})
```

### Orphan Prevention

```
Scenario: User is deleted from source system
Question: What happens to their Issues/PRs?

WRONG: CASCADE DELETE → All their contributions disappear
RIGHT: SET NULL on author_id → Contributions remain, author shows as "[deleted]"
```

### Scoring Matrix

| Score | Description |
|-------|-------------|
| **100** | All FKs constrained, proper cascade, unique constraints, no orphan risk |
| **95** | All critical constraints, minor gaps in cascade logic |
| **90** | Good FK coverage, appropriate cascades |
| **85** | Most FKs present, some cascade issues |
| **80** | Basic FK constraints, some missing |
| **70** | Missing critical unique constraints |
| **60** | Missing FK constraints |
| **50** | Incorrect cascades risking data loss |
| **0-49** | No integrity constraints |

### Red Flags (Automatic Deductions)
- Missing FK on relationship: -10 points per relationship
- `CASCADE ALL` on independent entities (User, Repository): -15 points
- No unique constraint on natural keys: -10 points
- Missing `orphanRemoval` on owned collections: -5 points
- Bidirectional relationship without proper `mappedBy`: -5 points

---

## Dimension 7: Documentation (Weight: 5%)

### What We're Measuring
Are fields documented with clear purpose, especially for non-obvious mappings and intentionally omitted fields?

### Required Documentation

#### Field-Level Comments
```java
public class PullRequest {
    /**
     * GitHub's GraphQL node ID (base64 encoded).
     * Example: "PR_kwDOBg7mcc5EzQpZ"
     */
    private String nodeId;
    
    /**
     * The numeric identifier from GitHub's REST API.
     * Used for backwards compatibility with v3 API integrations.
     */
    private Long databaseId;
    
    /**
     * The number of lines added in this PR.
     * Calculated by GitHub, may differ from actual diff due to move detection.
     */
    private Integer additions;
    
    /**
     * Whether the PR is in merge queue.
     * GitHub-specific field, null for GitLab merge requests.
     * @since GitHub added merge queues in 2022
     */
    private Boolean isInMergeQueue;
}
```

#### Omitted Fields Documentation
```java
/**
 * Represents a GitHub/GitLab Pull Request or Merge Request.
 * 
 * <h3>Intentionally Omitted Fields:</h3>
 * <ul>
 *   <li>{@code bodyHTML} - Can be rendered client-side from body Markdown</li>
 *   <li>{@code bodyResourcePath} - Internal GitHub path, not useful</li>
 *   <li>{@code viewerCanX} - Viewer-specific, not persisted</li>
 *   <li>{@code reactionGroups} - Stored in separate reactions table</li>
 *   <li>{@code timelineItems} - Too large/dynamic, fetched on demand</li>
 * </ul>
 * 
 * <h3>Platform-Specific Notes:</h3>
 * <ul>
 *   <li>GitHub: Uses {@code isDraft} for draft status</li>
 *   <li>GitLab: Uses {@code workInProgress} (mapped to isDraft)</li>
 * </ul>
 */
@Entity
public class PullRequest { ... }
```

#### Relationship Documentation
```java
/**
 * The user who authored this PR.
 * May be null if the user account was deleted from the platform.
 * In that case, display as "[deleted user]" in UI.
 */
@ManyToOne(fetch = FetchType.LAZY)
private User author;

/**
 * Reviews submitted on this PR.
 * Ordered by submittedAt ascending.
 * Only includes submitted reviews, not pending drafts.
 */
@OneToMany(mappedBy = "pullRequest")
@OrderBy("submittedAt ASC")
private List<PullRequestReview> reviews;
```

### Documentation Anti-Patterns

| Anti-Pattern | Example | Fix |
|--------------|---------|-----|
| Stating the obvious | `/** The title. */ String title;` | Omit or explain semantics |
| No units | `Integer timeout;` | `/** Timeout in milliseconds */` |
| No format | `String createdAt;` | `/** ISO 8601 format: 2024-01-15T10:30:00Z */` |
| Undocumented nullability | `User author;` | Explain when/why null |
| Missing enum values | `State state;` | Document all possible values |

### Scoring Matrix

| Score | Description |
|-------|-------------|
| **100** | Every non-obvious field documented, omissions explained, formats specified |
| **95** | Most fields documented, minor gaps |
| **90** | Good documentation coverage, some fields obvious enough |
| **85** | Key fields documented |
| **80** | Some documentation present |
| **70** | Minimal documentation |
| **60** | Only class-level documentation |
| **50** | Almost no documentation |
| **0-49** | No documentation |

### Red Flags (Automatic Deductions)
- No documentation on non-obvious field: -2 points per field
- No explanation for omitted critical fields: -5 points
- Missing nullability explanation on important fields: -3 points per field
- No class-level documentation: -5 points
- Outdated/incorrect documentation: -5 points per instance

---

## Calculating Final Score

### Formula
```
Final Score = (D1 × 0.25) + (D2 × 0.20) + (D3 × 0.15) + (D4 × 0.15) + (D5 × 0.10) + (D6 × 0.10) + (D7 × 0.05)
```

### Example Calculation

| Dimension | Raw Score | Weight | Weighted |
|-----------|-----------|--------|----------|
| Field Coverage | 85 | 0.25 | 21.25 |
| Type Correctness | 90 | 0.20 | 18.00 |
| Naming Consistency | 88 | 0.15 | 13.20 |
| GitLab Portability | 75 | 0.15 | 11.25 |
| Index Strategy | 80 | 0.10 | 8.00 |
| Data Integrity | 85 | 0.10 | 8.50 |
| Documentation | 70 | 0.05 | 3.50 |
| **Total** | | | **83.70 → B** |

---

## Grade Report Template

```markdown
# Entity Schema Grade Report: [EntityName]

**Final Grade: [LETTER]** ([SCORE]/100)
**Date:** YYYY-MM-DD
**Reviewer:** [Name]

## Summary
[One paragraph overall assessment]

## Dimension Scores

| Dimension | Score | Notes |
|-----------|-------|-------|
| Field Coverage (25%) | XX/100 | [Brief notes] |
| Type Correctness (20%) | XX/100 | [Brief notes] |
| Naming Consistency (15%) | XX/100 | [Brief notes] |
| GitLab Portability (15%) | XX/100 | [Brief notes] |
| Index Strategy (10%) | XX/100 | [Brief notes] |
| Data Integrity (10%) | XX/100 | [Brief notes] |
| Documentation (5%) | XX/100 | [Brief notes] |

## Critical Issues (Must Fix)
1. [Issue description and line reference]
2. ...

## Recommendations (Should Fix)
1. [Recommendation]
2. ...

## Nice to Have
1. [Optional improvement]
2. ...
```

---

## Appendix: Quick Checklists

### Before Submitting Entity for Review

- [ ] All critical fields from source API captured
- [ ] Column types match source types (especially BIGINT for IDs)
- [ ] Nullability matches source API (!-marked fields are NOT NULL)
- [ ] Enums used for known value sets
- [ ] Java: camelCase, DB: snake_case
- [ ] Provider discriminator present if applicable
- [ ] FK constraints on all relationships
- [ ] Indexes on FKs and common query columns
- [ ] Unique constraints on natural keys
- [ ] Non-obvious fields documented
- [ ] Omitted fields documented with rationale

### Common Issues That Drop Grades

| Issue | Typical Point Loss |
|-------|-------------------|
| Missing `databaseId` field | -15 to -20 |
| `INT` instead of `BIGINT` for IDs | -15 |
| No timezone on timestamps | -10 |
| No index on `login`/`email` | -10 |
| No FK constraint | -10 per relationship |
| GitHub-specific naming without abstraction | -5 per field |
| Missing documentation on nullable fields | -3 per field |
