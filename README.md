# PR assets — #1350 (v2)

Screenshots for [PR #1350](https://github.com/ls1intum/Hephaestus/pull/1350)
(admin integration sync redesign).

Regenerated after the admin UI was substantially rebuilt. The earlier set lives on
`assets/pr-1350` and is left in place, because existing PR comments link to it and
overwriting those images would silently rewrite what past reviewers saw.

New in this set: `08-slack-channels` and `09-outline-collections` — Slack and Outline
now get the same per-resource sync ledger the SCM integrations have.

Rendered from the Storybook stories that ship with that PR
(`webapp/src/components/admin/integrations/*.stories.tsx`), light theme.

| File | Story id |
| --- | --- |
| `01-freshness-matrix.png` | `components-admin-integrations-syncresourcestable--watermark-divergence` |
| `02-status-header-healthy.png` | `components-admin-integrations-syncstatusheader--healthy` |
| `03-status-header-stale.png` | `components-admin-integrations-syncstatusheader--very-stale-freshness` |
| `04-backfilling.png` | `components-admin-integrations-syncresourcestable--backfilling` |
| `05-job-history.png` | `components-admin-integrations-syncjobstable--all-statuses` |
| `06-overview-card.png` | `components-admin-integrations-integrationoverviewcard--with-errored-and-stale-resources` |
| `07-not-tracked-vs-never.png` | `components-admin-integrations-syncresourcestable--never-synced` |
| `08-slack-channels.png` | `components-admin-integrations-syncresourcestable--slack-channels` |
| `09-outline-collections.png` | `components-admin-integrations-syncresourcestable--outline-collections` |

This is an orphan branch: it carries no code and is never merged. It exists so the
PR body can embed images without committing binaries into the source history.
