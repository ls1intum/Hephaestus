<div align="center">
  <img alt="Hephaestus Logo" height="100px" src="./docs/static/img/brand/hammer_bg.svg">

  [![CI/CD](https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml/badge.svg)](https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml)
  [![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/ls1intum/Hephaestus/blob/main/LICENSE)
  [![Docs](https://img.shields.io/badge/docs-live-brightgreen)](https://ls1intum.github.io/Hephaestus/)
  [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/ls1intum/Hephaestus/blob/main/CONTRIBUTING.md)
</div>

# Hephaestus

Better feedback on every pull request. Hephaestus reviews each contribution against the practices your project cares about, and tells the contributor — clearly, with examples — what to do next.

<img alt="Hephaestus mascot" height="200px" src="./docs/user/img/overview/agile_hephaestus.png">

Works with GitHub today; GitLab covers ingestion and review while we close the gap. Self-hostable, with the model provider of your choice.

## How it works

Each project keeps a short list of the practices that matter — a clear pull-request description, a thoughtful review, a coherent commit history. Hephaestus reads from that list. When a contribution comes in, it reads the description, the diff, the review thread, the related issues, and the contributor's recent history, and writes a comment beside the change.

The advice changes with the contributor. A first-time issue gets a worked example. A repeat issue gets a sharper note. Steady improvement gets a question to think on.

Open the mentor when you want to think out loud. Ask what to focus on this week, talk through what's stuck, plan the next push. Your profile is private to you.

## Architecture

<img alt="Architecture" src="./docs/diagrams/architecture.svg" width="800">

A Spring Boot server, a TypeScript service for the mentor, and a sandboxed agent runner — fronted by a React webapp and Keycloak. The agent and the mentor each talk to the model provider you choose.

For entities and how they map to the codebase, see the [Conceptual Model](https://ls1intum.github.io/Hephaestus/contributor/conceptual-model).

## Where we're going

- A place inside Hephaestus to read your findings and respond to them, alongside the comments on the pull request.
- An editor for the practice list, in the admin UI.
- Recognition that reflects how you're growing on the practices you care about, replacing the activity-shaped scoring that's there today.
- Lighter touch as you improve — less repetition, more reflection.
- Closing the gap between GitHub and GitLab.
- A mentor that grows with the work — better at planning, better at reflection, grounded in everything you've done.

## Documentation

- Docs: <https://ls1intum.github.io/Hephaestus/>
- UI: [Storybook](https://main--66a8981a27ced8fef3190d41.chromatic.com/)
- Run it locally: [contributor guide](https://ls1intum.github.io/Hephaestus/contributor/local-development)

## Contributing

We welcome contributions from members of our organisation and from outside it. Use a real name and a real photo, follow the [contribution guide](./CONTRIBUTING.md), and we'll meet you in the review.
