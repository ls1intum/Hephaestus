# Hephaestus Documentation

[![Documentation Status](https://github.com/ls1intum/Hephaestus/actions/workflows/cd-docs.yml/badge.svg)](https://github.com/ls1intum/Hephaestus/actions/workflows/cd-docs.yml)

**Live Site:** [https://ls1intum.github.io/Hephaestus/](https://ls1intum.github.io/Hephaestus/)

This site is powered by [Docusaurus 3](https://docusaurus.io/) with Rspack, SWC, and LightningCSS for 2-4x faster builds. The content is split into three guides:

- **User Guide** — End-user workflows (mentor sessions, leaderboard, workspace management)
- **Contributor Guide** — Engineering guides, ERD + StarUML assets, and local development setup
- **Admin Guide** — Production deployment runbooks

## Quick Start

```bash
# From repo root (recommended)
npm run docs:dev       # Start dev server at http://localhost:3000/Hephaestus/

# Or from docs directory
cd docs && npm start
```

## Prerequisites

- Node.js ≥ 22.22 (matches `.node-version`)
- npm ≥ 10.8

Install dependencies:

```bash
npm install   # in docs directory
# or from repo root: cd docs && npm install
```

## Available Scripts

Run from repo root:

```bash
npm run docs:dev     # Start development server
npm run docs:build   # Build for production
npm run docs:serve   # Preview production build
npm run docs:lint    # TypeScript + Markdown linting
```

## Quality Gates

CI runs these checks automatically:

1. **TypeScript check** — `npm run typecheck`
2. **Markdown lint** — `npm run lint:md`
3. **Build with strict validation** — `npm run build`

The build fails on:

- Broken internal links (`onBrokenLinks: 'throw'`)
- Broken Markdown links (`onBrokenMarkdownLinks: 'throw'`)
- Broken anchor links (`onBrokenAnchors: 'throw'`)

## Performance

Using **Docusaurus Faster** for optimized builds:

- **Rspack** — Rust-based bundler (replaces Webpack)
- **SWC** — Fast JS/TS transpilation and minification
- **LightningCSS** — Fast CSS processing

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
