# CI Pipeline Grading Rubric

This document grades the Hephaestus CI pipeline against industry best practices.
Last updated: 2025-12-31

## Baseline Metrics (Before Optimization)

| Metric                  | Value   | Notes                               |
| ----------------------- | ------- | ----------------------------------- |
| **Total CI Time (P50)** | 8-9 min | Average of last 6 successful runs   |
| **Total CI Time (P90)** | 10 min  | Occasional slower runs              |
| **Longest Job**         | 6+ min  | `application-server-build` (Docker) |
| **Total Jobs**          | ~29     | Many parallel jobs in matrix        |
| **Cache Hit Rate**      | ~80%    | Good npm/Maven caching              |

### Job Duration Breakdown (Critical Path)

| Job                                | Duration | Bottleneck                   |
| ---------------------------------- | -------- | ---------------------------- |
| `application-server-build (arm64)` | 370s     | Docker multi-arch build      |
| `application-server-build (amd64)` | 353s     | Docker build                 |
| `sast` (CodeQL)                    | 344s     | Static analysis              |
| `webapp-build (arm64)`             | 287s     | Docker build                 |
| `webapp-build (amd64)`             | 275s     | Docker build                 |
| `webapp-quality`                   | 182s     | npm ci + biome + tsc + build |
| `webapp-visual`                    | 180s     | Storybook + Chromatic        |
| `database-models-validation`       | 155s     | Docker PostgreSQL + Maven    |
| `intelligence-service-quality`     | 138s     | npm ci + biome + tsc         |

---

## Grading Rubric v1.0

### 1. Speed (Weight: 30%)

| Grade | Criteria           | Current               |
| ----- | ------------------ | --------------------- |
| A+    | Total CI < 5 min   |                       |
| A     | Total CI 5-7 min   |                       |
| B     | Total CI 7-10 min  | **Current: ~8-9 min** |
| C     | Total CI 10-15 min |                       |
| D     | Total CI 15-20 min |                       |
| F     | Total CI > 20 min  |                       |

**Current Grade: B**

**Issues Identified:**

- Docker builds are the critical path (6+ min)
- Multiple jobs run `npm ci` redundantly (even with cache)
- Database validation jobs spin up PostgreSQL containers individually
- No path-based filtering for selective job execution

---

### 2. Caching Strategy (Weight: 20%)

| Grade | Criteria                                                             | Current     |
| ----- | -------------------------------------------------------------------- | ----------- |
| A+    | All cacheable artifacts cached with optimal keys, near-100% hit rate |             |
| A     | Strong caching with restore-keys fallback, 90%+ hit rate             |             |
| B     | Basic caching implemented, 70-90% hit rate                           | **Current** |
| C     | Partial caching, some redundant downloads                            |             |
| D     | Minimal caching                                                      |             |
| F     | No caching                                                           |             |

**Current Grade: B**

**What's Working:**

- npm cache via `actions/setup-node`
- Maven cache via `actions/setup-java`
- Custom `setup-caches` action for node_modules
- Webapp build artifact caching

**Issues Identified:**

- Jobs run `rm -rf node_modules && npm ci` despite caching
- No Docker layer caching in external workflow
- Redundant cache keys (different per matrix job)
- Some jobs bypass cache with `npm install` instead of `npm ci`
- Storybook build not cached separately

---

### 3. Parallelization (Weight: 20%)

| Grade | Criteria                                           | Current     |
| ----- | -------------------------------------------------- | ----------- |
| A+    | Maximum parallelization, no sequential bottlenecks |             |
| A     | Good parallelization with matrix builds            | **Current** |
| B     | Moderate parallelization, some sequential jobs     |             |
| C     | Limited parallelization                            |             |
| D     | Mostly sequential                                  |             |
| F     | Fully sequential                                   |             |

**Current Grade: A**

**What's Working:**

- Matrix strategy for quality gates (8 parallel jobs)
- Matrix strategy for tests (4 parallel jobs)
- Matrix strategy for Docker builds (4 components x 2 archs)
- Matrix strategy for security scans (3 parallel jobs)
- All major workflows run in parallel after pre-job

**Issues Identified:**

- `all-ci-passed` job waits for all jobs (intended, but adds ~10s)
- Some jobs have internal sequential steps that could parallelize

---

### 4. Resource Efficiency (Weight: 15%)

| Grade | Criteria                                             | Current     |
| ----- | ---------------------------------------------------- | ----------- |
| A+    | Right-sized runners, zero waste, optimal concurrency |             |
| A     | Good resource use, minimal redundancy                |             |
| B     | Some redundancy in setup/dependencies                | **Current** |
| C     | Significant redundancy                               |             |
| D     | Poor resource utilization                            |             |
| F     | Wasteful resource use                                |             |

**Current Grade: B**

**What's Working:**

- Uses `ubuntu-latest` (standard sizing)
- Uses ARM runners for Docker (external workflow)
- `fetch-depth: 0` only where needed (git history)
- Concurrency groups prevent duplicate runs

**Issues Identified:**

- Each quality gate job reinstalls dependencies independently
- Database validation jobs each spin up their own PostgreSQL container
- Multiple npm ci runs across jobs (9+ separate installs)
- No shallow checkout where full history isn't needed (most jobs use `fetch-depth: 0`)

---

### 5. Robustness (Weight: 10%)

| Grade | Criteria                                        | Current     |
| ----- | ----------------------------------------------- | ----------- |
| A+    | Retries, timeouts, clear errors, zero flakiness |             |
| A     | Good error handling, rare flakiness             | **Current** |
| B     | Basic error handling, occasional issues         |             |
| C     | Some flaky tests or hanging jobs                |             |
| D     | Frequent failures due to infrastructure         |             |
| F     | Unreliable pipeline                             |             |

**Current Grade: A**

**What's Working:**

- Timeouts on all jobs (15-30 min)
- `fail-fast: false` for matrix jobs
- `continue-on-error: true` where appropriate
- Retry logic in webapp-quality (npm ci fallback)
- Docker health checks for PostgreSQL
- `skip-duplicate-actions` for efficiency
- Concurrency with `cancel-in-progress`

**Issues Identified:**

- Some retry logic is duplicated (could be a composite action)
- Error messages could be more actionable in some cases

---

### 6. Maintainability (Weight: 5%)

| Grade | Criteria                                         | Current     |
| ----- | ------------------------------------------------ | ----------- |
| A+    | DRY, reusable workflows, excellent documentation |             |
| A     | Good structure, reusable components              | **Current** |
| B     | Moderate duplication, reasonable structure       |             |
| C     | Some copy-paste, hard to maintain                |             |
| D     | Significant duplication                          |             |
| F     | Unmaintainable spaghetti                         |             |

**Current Grade: A**

**What's Working:**

- Reusable workflow pattern (workflow_call)
- Custom composite action (`setup-caches`)
- External shared workflow for Docker builds
- Clear job naming and structure
- Comments explaining purpose

**Issues Identified:**

- Setup steps repeated across multiple workflows
- Platform binary installation duplicated in multiple places
- Could consolidate database jobs into single container

---

## Overall Grade: B+

| Category            | Weight | Grade | Points     |
| ------------------- | ------ | ----- | ---------- |
| Speed               | 30%    | B     | 2.4        |
| Caching             | 20%    | B     | 1.6        |
| Parallelization     | 20%    | A     | 1.6        |
| Resource Efficiency | 15%    | B     | 1.2        |
| Robustness          | 10%    | A     | 0.8        |
| Maintainability     | 5%     | A     | 0.4        |
| **Total**           | 100%   |       | **8.0/10** |

---

## Optimization Opportunities (Prioritized)

### High Impact (Target: 2-3 min savings)

1. **Path-based filtering** - Skip unchanged components

   - If only `webapp/` changed, skip `application-server-*` jobs
   - If only `docs/` changed, skip all main CI
   - Estimated savings: 30-50% of runs

2. **Reduce shallow checkout depth** - Use `fetch-depth: 1` where possible

   - Most jobs don't need git history
   - Estimated savings: 5-10s per job

3. **Docker layer caching** - External workflow improvements needed

   - Currently builds from scratch each time
   - Estimated savings: 30-60s per Docker job

4. **Consolidate database jobs** - Single PostgreSQL container
   - Three jobs currently spin up separate containers
   - Could share one container with service
   - Estimated savings: 60-90s total

### Medium Impact (Target: 1-2 min savings)

5. **Optimize npm ci calls** - Avoid redundant reinstalls

   - Don't `rm -rf node_modules` when cache is valid
   - Use cache hit output to skip npm ci
   - Estimated savings: 10-20s per job

6. **Separate Storybook build cache**

   - Dedicated cache for storybook-static
   - Estimated savings: 10-30s

7. **Skip CI for docs-only changes**
   - Already implemented in cd-docs.yml, extend to main CI
   - Estimated savings: full CI time on docs changes

### Lower Impact (Nice to have)

8. **Composite actions for common patterns**

   - Platform binary installation
   - PostgreSQL container setup
   - Better maintainability

9. **GitHub Actions arm64 runners for webapp**
   - Native arm64 testing if needed
   - Currently amd64 only for most jobs

---

## Success Metrics

| Metric                     | Current | Target | Stretch |
| -------------------------- | ------- | ------ | ------- |
| P50 CI Time                | 8-9 min | 6 min  | 5 min   |
| P90 CI Time                | 10 min  | 8 min  | 6 min   |
| Cache Hit Rate             | ~80%    | 90%    | 95%     |
| Skipped Jobs (path filter) | 0%      | 40%    | 60%     |

---

## Optimization Round 1: Path-Based Filtering (IMPLEMENTED)

### Changes Implemented

1. **Added `dorny/paths-filter` to detect changed components** (`cicd.yml`)

   - Detects: webapp, application-server, intelligence-service, webhook-ingest, docs, ci-config
   - Outputs passed to all downstream workflows with proper fallback logic
   - CI config changes trigger all checks (validates workflow changes)

2. **Quality Gates workflow (`ci-quality-gates.yml`)**

   - Added path-based inputs for each component
   - Jobs skip execution if their component didn't change
   - Force-run on CI config changes or non-PR events

3. **Tests workflow (`ci-tests.yml`)**

   - Added path-based inputs for webapp and application-server
   - Jobs skip execution if their component didn't change
   - Full git history preserved for Chromatic TurboSnap

4. **Docker Build workflow (`ci-docker-build.yml`)**

   - Converted from matrix to individual jobs with conditions
   - Each component only builds if its files changed
   - CI config changes trigger all builds (Dockerfiles may have changed)

5. **Security Scan workflow (`ci-security-scan.yml`)**
   - Shallow checkout for SAST and dependencies scans
   - Node setup only for SAST (not needed for other scans)

### Measured Results (Full CI Run with CI Config Changes)

**Total CI Time: 7 minutes** (baseline was 8-9 min)

| Job Category                | Duration | Notes                       |
| --------------------------- | -------- | --------------------------- |
| Docker - application-server | 374s     | Critical path (longest job) |
| Security - SAST             | 325s     | CodeQL analysis             |
| Docker - webapp             | 301s     | Multi-arch build            |
| Quality Gates (8 jobs)      | 84-178s  | All passed                  |
| Tests (4 jobs)              | 43-187s  | All passed                  |

### Expected Impact (Component-Specific PRs)

| Scenario                         | Before  | After (Est.) | Savings |
| -------------------------------- | ------- | ------------ | ------- |
| All components/CI config changed | 8-9 min | 7 min        | ~20%    |
| Only webapp changed              | 8-9 min | 4-5 min      | ~45%    |
| Only application-server changed  | 8-9 min | 5-6 min      | ~35%    |
| Only docs changed                | 8-9 min | < 1 min      | ~90%    |

---

## Updated Grades After Optimization

| Category            | Weight | Before  | After   | Notes                           |
| ------------------- | ------ | ------- | ------- | ------------------------------- |
| Speed               | 30%    | B       | B+      | Path filtering reduces avg time |
| Caching             | 20%    | B       | B       | No change yet                   |
| Parallelization     | 20%    | A       | A       | Already excellent               |
| Resource Efficiency | 15%    | B       | A-      | Skipped jobs save compute       |
| Robustness          | 10%    | A       | A       | No regressions                  |
| Maintainability     | 5%     | A       | A-      | Slightly more complex           |
| **Total**           | 100%   | **8.0** | **8.4** | +5% improvement                 |

---

## Revision History

| Version | Date       | Changes                                         |
| ------- | ---------- | ----------------------------------------------- |
| 1.0     | 2025-12-31 | Initial rubric, baseline analysis               |
| 1.1     | 2025-12-31 | Implemented path-based filtering                |
| 1.2     | 2025-12-31 | Fixed ci-config fallback, Chromatic git history |
