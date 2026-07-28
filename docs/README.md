# Hephaestus Documentation

[![Documentation Status](https://github.com/ls1intum/Hephaestus/actions/workflows/cd-docs.yml/badge.svg)](https://github.com/ls1intum/Hephaestus/actions/workflows/cd-docs.yml)

**Live Site:** [https://ls1intum.github.io/Hephaestus/](https://ls1intum.github.io/Hephaestus/)

This site is powered by [Docusaurus 3](https://docusaurus.io/) with Rspack, SWC, and LightningCSS for 2-4x faster builds. The content is split into three guides:

- **User Guide** (`user/`) – End-user workflows (mentor sessions, leaderboard, workspace management)
- **Contributor Guide** (`contributor/`) – Engineering guides, ERD + StarUML assets, and local development setup
- **Admin Guide** (`admin/`) – Install, integrations, and production operations

Those three directories are the whole site: each is one `@docusaurus/plugin-content-docs` instance in
`docusaurus.config.ts`, and every page in them must be reachable from its sidebar.

## What is deliberately *not* published

`decisions/`, `runbooks/`, `auth-architecture.md` and `auth-glossary.md` are repo-only reference
material — no plugin serves them, and they are read on GitHub. See
[`decisions/README.md`](decisions/README.md) for why, and for the rule that a published page links
into them by absolute GitHub URL rather than a relative path.

## Quick Start

```bash
# From repo root (recommended)
pnpm run docs:dev       # Start dev server at http://localhost:3000/Hephaestus/

# Or from docs directory
cd docs && npm start
```

## Prerequisites

- Node.js ≥ 22.22 (matches `.node-version`)
- npm ≥ 10.8

Install dependencies:

```bash
pnpm install   # in docs directory
# or from repo root: cd docs && pnpm install
```

## Available Scripts

Run from repo root:

```bash
pnpm run docs:dev     # Start development server
pnpm run docs:build   # Build for production
pnpm run docs:serve   # Preview production build
pnpm run docs:lint    # TypeScript + Markdown linting
```

## Quality Gates

CI runs these checks automatically:

1. **TypeScript check** – `pnpm run typecheck`
2. **Markdown lint** – `pnpm run lint:md`
3. **Build with strict validation** – `pnpm run build`

The build fails on:

- Broken internal links (`onBrokenLinks: 'throw'`)
- Broken Markdown links (`onBrokenMarkdownLinks: 'throw'`)
- Broken anchor links (`onBrokenAnchors: 'throw'`)

## Performance

Using **Docusaurus Faster** for optimized builds:

- **Rspack** – Rust-based bundler (replaces Webpack)
- **SWC** – Fast JS/TS transpilation and minification
- **LightningCSS** – Fast CSS processing

Result: 2-4x faster build times compared to default Docusaurus configuration.

## Content Structure

```text
docs/
├── user/          # End-user documentation
├── contributor/   # Developer & contributor guides
├── admin/         # Production setup & operations
├── static/        # Static assets (images, files)
│   ├── robots.txt # SEO crawling rules
│   └── img/       # Images and brand assets
├── src/           # Custom React components and CSS
└── docusaurus.config.ts  # Main configuration
```

## Deployment

Deployment is handled automatically via GitHub Actions:

- **Push to `main`** → Deploys to GitHub Pages
- **Pull Requests** → Deploys preview to Surge.sh with URL comment
- **PR Close** → Tears down Surge.sh preview

Manual deployment is not recommended. Use the CI/CD pipeline.
