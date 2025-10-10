---
id: documentation
sidebar_position: 4
title: Documentation Standards
---

## Authoring principles

- Source of truth for product and engineering docs lives in this Docusaurus workspace.
- Each significant feature change must include a docs update in the same pull request.
- Prefer task-oriented guides over exhaustive reference dumps.

## Structure

- **User docs**: authored in `docs/docs/user`, focused on feature outcomes and guided tours.
- **Contributor docs**: authored in `docs/docs/contributor`, covering architecture, setup, and operational playbooks.
- **Blog**: use for changelogs, release announcements, and research highlights.

## Writing workflow

1. Run `npm start` in the `docs/` workspace for live preview.
2. Place accompanying assets in `docs/static/img` and reference using relative paths (`/img/...`).
3. Co-locate stories or diagrams with the relevant article; large diagrams go into `docs/static/diagrams`.

## Review checklist

- ✅ Short title and summary front matter (`description`) when relevant.
- ✅ Screenshots include captions and dark/light mode friendly colours.
- ✅ Internal links use relative paths (`../`) to keep cross-version builds safe.
- ✅ Code blocks specify language identifiers for syntax highlighting.

## Publishing

Documentation builds ship via the same CI pipeline as code:

- `npm run lint:docs` checks Markdown styles.
- `npm run build` validates the static output.
- The `gh-pages` branch hosts the production site at [https://ls1intum.github.io/Hephaestus/](https://ls1intum.github.io/Hephaestus/).
