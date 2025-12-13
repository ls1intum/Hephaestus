## Description

<!-- Provide a clear summary of your changes. Use "Copilot Summary" button above to auto-generate. -->

Fixes # <!-- Link to issue if applicable -->

## Type of change

<!-- Check the ONE that applies. Add `!` to PR title for breaking changes. -->

- [ ] 🐛 Bug fix (non-breaking change that fixes an issue)
- [ ] ✨ Feature (non-breaking change that adds functionality)
- [ ] 💥 Breaking change (causes existing functionality to change)
- [ ] 📚 Documentation update
- [ ] 🔧 Refactor / chore (no functional changes)

## Testing

<!-- How did you verify this works? Reviewers need confidence—show your evidence. -->

- [ ] Tested locally with steps described below
- [ ] Covered by new/existing automated tests
- [ ] N/A (docs, config, or trivial change)

**Test steps:**
<!-- Describe manual testing steps, or write "CI covers this" if purely automated -->

## Screenshots / Recordings

<!-- For UI changes: attach screenshots or a Loom recording. Delete this section if N/A. -->

---

<details>
<summary>📋 Contributor checklist</summary>

_Most of these are verified by CI. Check as a self-review reminder._

- [ ] PR title follows [Conventional Commits](https://www.conventionalcommits.org/) (`feat(scope): description`)
- [ ] Self-reviewed the diff for obvious issues
- [ ] Considered edge cases and error handling
- [ ] Updated documentation if behavior changed
- [ ] For UI: checked responsive design and added Storybook story
- [ ] For API: regenerated clients if schema changed (`npm run generate:api`)
- [ ] For breaking changes: updated `MIGRATION.md`

</details>
