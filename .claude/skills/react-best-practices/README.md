# react-best-practices

A vendored snapshot of Vercel Engineering's React performance pack (MIT, see `SKILL.md` frontmatter).
There is no build step here: `AGENTS.md` is the compiled form of `rules/*.md` as shipped upstream, and
editing either in place makes the next re-vendor a conflict rather than an overwrite.

- `SKILL.md` — what the loader reads. Its applicability section is the part this repo maintains, and
  it is the part to read first: much of the pack is Next.js/RSC-only and does not apply to this SPA.
- `rules/*.md` — one rule each; `_sections.md` carries the section order and impact levels.
- `AGENTS.md` — every rule expanded into one document.
