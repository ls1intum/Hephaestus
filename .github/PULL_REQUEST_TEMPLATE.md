<!-- 
PR TITLE: Use Conventional Commits format
  <type>(<scope>): <description>
  
  Types: feat, fix, docs, refactor, test, chore, ci, perf, revert
  Scopes: webapp, server, ai, webhooks, docs | ci, deps, docker, db | leaderboard, mentor, profile, workspace, teams, github, notifications
  Breaking changes: Add ! before : (e.g., feat!: or feat(scope)!:)
  
  Examples:
    feat(leaderboard): add weekly ranking view
    fix(server): handle null repository owner
    refactor(ai): simplify prompt builder
-->

## Summary
<!-- What does this PR do? One sentence. -->

## Changes
<!-- Bullet list of key changes. Keep it scannable. -->

- 

## Testing
<!-- How did you verify this works? -->

- [ ] Tested locally
- [ ] Added/updated tests

## Checklist
<!-- Quick sanity checks before review -->

- [ ] PR title follows conventional commit format
- [ ] Self-reviewed for obvious issues
- [ ] No console errors/warnings added

---
<details>
<summary>📋 Extended checklist (optional)</summary>

**For UI changes:**
- [ ] Works on mobile/tablet viewports
- [ ] Added Storybook story

**For API changes:**
- [ ] Regenerated OpenAPI client (`npm run generate:api`)
- [ ] Updated MIGRATION.md if breaking

**For database changes:**
- [ ] Added Liquibase changelog
- [ ] Ran `npm run db:generate-erd-docs`

</details>
