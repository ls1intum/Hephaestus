# Hephaestus Documentation

The documentation site is built with [Docusaurus 3](https://docusaurus.io/docs). All content lives in `docs/docs` and is written in Markdown or MDX.

## Prerequisites

- [Node.js](https://nodejs.org/) 22.10 or later (LTS)
- npm 10.8 or later

## Install dependencies

From the repository root run:

```bash
npm install
```

The command installs dependencies for the docs workspace and other packages in the monorepo.

## Local development

Start a local dev server with live reload:

```bash
npm run docs:dev
```

The site is served at `http://localhost:3000`. Changes to Markdown/MDX files or configuration are reloaded automatically.

## Production build

To generate a static production build run:

```bash
npm run docs:build
```

Preview the build locally with:

```bash
npm run docs:serve
```

The static assets are written to `docs/build` and can be deployed to any static hosting provider.
