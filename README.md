<div align="center">
  <img alt="Hephaestus Logo" height="100px" src="./docs/static/img/brand/hammer_bg.svg">

  [![CI/CD](https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml/badge.svg)](https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml)
  [![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/ls1intum/Hephaestus/blob/main/LICENSE)
  [![Docs](https://img.shields.io/badge/docs-live-brightgreen)](https://ls1intum.github.io/Hephaestus/)
  [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/ls1intum/Hephaestus/blob/main/CONTRIBUTING.md)
</div>

# Hephaestus

Hephaestus is a research instrument and an open-source platform for **practice-aware feedback in software project work**. Each project keeps a short list of the practices that matter — describing a change so it can be reviewed, scoping work into reviewable units, leaving an actionable comment, following through on a commitment. When a pull request comes in, a comment appears alongside the existing review, with evidence and a suggested next move.

<img alt="Hephaestus mascot" height="200px" src="./docs/user/img/overview/agile_hephaestus.png">

Works with GitHub and GitLab. Self-hostable, with the AI model provider of your choice.

## How it works

A project keeps a list of the practices that matter to it. When a pull request comes in, the contribution and that list are read together — the description, the diff, the review thread, the related issues, the contributor's recent history. A comment appears beside the change, with the evidence behind it and a suggested next move. Take what fits. Push back on what doesn't.

A mentor sits in the app for when you want to think out loud — a place to plan, look back, or talk through what's stuck.

## Architecture

<img alt="Architecture" src="./docs/diagrams/architecture.svg" width="800">

For entities and how they map to the codebase, see the [Conceptual Model](https://ls1intum.github.io/Hephaestus/contributor/conceptual-model).

## Where we're going

- **A reflection dashboard inside Hephaestus** for reading and responding to findings, alongside the comments on the pull request.
- **In-app contestation.** The API exists today; the UI is next.
- **A practice-list editor in the admin UI**, so the catalog can be shaped without editing seed data.
- **Recognition that reflects practice growth**, replacing the activity-shaped scoring that's there today.
- **Lighter touch as you improve** — less repetition, more reflection.
- **Closing the gap between GitHub and GitLab** on the small surfaces that still differ.
- **A mentor that grows with the work** — closer to the work as it accumulates.

## Documentation

- Docs: <https://ls1intum.github.io/Hephaestus/>
- UI: [Storybook](https://main--66a8981a27ced8fef3190d41.chromatic.com/)
- Run it locally: [contributor guide](https://ls1intum.github.io/Hephaestus/contributor/local-development)

## Contributing

We welcome contributions from members of our organisation and from outside it. Use a real name and a real photo, and follow the [contribution guide](./CONTRIBUTING.md).
