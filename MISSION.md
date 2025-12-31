# Mission: GitProvider Module - Brutal Polish

## Problem Statement
The gitprovider module is our ETL system from GitHub to Hephaestus. PR #621 removed hub4j and introduced GraphQL-based sync. Now we need RUTHLESS polishing to ensure:
- Zero sync gaps (no missing data, no stale records)
- Comprehensive field coverage for Hephaestus context
- Future-proof architecture for GitLab support
- Industry-leading reliability

## Critical Context
- **CI is failing**: `database-models-validation` - the schema.ts has `issueType` table in wrong order. Fix: `npm run db:generate-models:intelligence-service`
- **Live tests exist**: Copy credentials from main worktree's `application-live-local.yml` if available
- **GraphQL schema**: Analyze what GitHub exposes vs what we sync - are we missing fields we'll regret?

## Success Criteria

### Immediate (CI Fix)
- [ ] Regenerate db models to fix ordering issue
- [ ] CI passing green

### GitProvider Polishing
- [ ] Analyze GitHub GraphQL schema exhaustively - document any fields we're NOT syncing that Hephaestus might need
- [ ] Verify fullDatabaseId (BigInt) is used everywhere, no integer overflow risk
- [ ] Check sync reliability: Are we handling pagination correctly? Rate limits? Retries?
- [ ] Webhook handling: Are we processing all events we need? Any gaps?
- [ ] Entity relationships: Issue dependencies, parent-child relationships - are we ready for these?
- [ ] Code quality: Factory methods, DTOs, mixins - are they A+ quality or can we be MORE brutal?

### Research (Websearch Required)
- Search for "GitHub GraphQL sync best practices 2025"
- Search for "ETL reliability patterns event sourcing"
- Search for "GitHub API rate limiting strategies production"
- Search for "GraphQL cursor pagination edge cases"
- Challenge any A+ rubric - what would an S-tier implementation look like?

## Constraints
- NO feature regression - existing functionality must work
- NO hub4j dependencies - pure GraphQL
- Module should have ZERO dependencies on other Hephaestus modules (standalone ETL)
- Keep GitLab extensibility in mind (abstract where it makes sense)

## Architecture Invariants
- gitprovider package must not import from activity, leaderboard, profile, badpractice, workspace, etc.
- Only exports: entities, DTOs, sync services, repository interfaces
- Consumers depend on gitprovider, never the reverse

## Be Ruthless
- Challenge every design decision
- Question every field we sync (or don't sync)
- Look for edge cases that would cause data loss
- Find reliability gaps before production does
- If you think something is "good enough" - it's not. Make it exceptional.
