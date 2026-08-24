@AGENTS.md

## Claude Code

`webapp/CLAUDE.md` and `server/CLAUDE.md` import their own trees' guides the same way, and must
**not** be imported from here: a nested `CLAUDE.md` loads only once Claude reads a file in that tree,
which is what keeps each package guide out of the sessions that never enter it.
`docs/contributor/ai-agent-workflow.mdx` has the rest.
