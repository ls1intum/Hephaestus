<div align="center">
  <img alt="Hephaestus Logo" height="100px" src="./docs/static/img/brand/hammer_bg.svg">

  [![CI/CD](https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml/badge.svg)](https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml)
  [![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/ls1intum/Hephaestus/blob/main/LICENSE)
  [![Docs](https://img.shields.io/badge/docs-live-brightgreen)](https://ls1intum.github.io/Hephaestus/)
  [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/ls1intum/Hephaestus/blob/main/CONTRIBUTING.md)
</div>

# Hephaestus — Practice-Aware Feedback for Software Project Work

Hephaestus turns review activity into **practice-aware feedback** contributors actually use. You define the practices that matter for your project; Hephaestus reads the full pull-request lifecycle, evaluates each contribution against your catalog, and routes findings — with evidence and a recommended action — through the channel that fits the moment.

<img alt="Hephaestus mascot" height="200px" src="./docs/user/img/overview/agile_hephaestus.png">

> Open-source. Self-hostable. Bring your own LLM provider. Integrates with GitHub (full) and GitLab (webhook ingestion + practice detection).

## What this is not

- **Not a leaderboard.** Aggregate scoring underperforms task- and process-level feedback. Findings target the practice and the action — never the contributor's worth.
- **Not a defect-catching review bot.** Tools like CodeRabbit and Greptile annotate the diff. Hephaestus reads the whole PR lifecycle and produces *practice* findings; the two stack cleanly.
- **Not a coding agent.** The conversational mentor is a reflection partner. It does not write code, merge branches, or take actions on your behalf.

## The practice-aware loop

### Detect

A versioned, inspectable **practice catalog** belongs to each workspace. An AI agent runs in a sandboxed Docker container on every contribution and reads the full pull-request lifecycle — descriptions, commits, review threads, related issues, and the contributor's prior history. Output: structured findings with verdict, severity, evidence, and a recommended action.

### Coach

Findings adapt to each contributor's track record per practice. New contributors get concrete examples; repeat issues get direct coaching; improving contributors get reflection prompts. The **conversational mentor** complements in-context findings as a reflection partner — surfacing your activity, asking what's blocking you, and helping you plan.

### Reflect

Reflection surfaces show patterns over time, scoped privately. Contributors see their own findings and practice history. Facilitators see aggregate practice signals on a dashboard built for coaching, not grading. Workspaces that want a weekly activity recognition surface can opt into one — never the headline.

## Architecture

<img alt="Hephaestus Architecture" src="./docs/diagrams/architecture.svg" width="800">

### Domain model

<img alt="Hephaestus Domain Model" src="./docs/diagrams/domain-model.svg" width="800">

For entities, channels, and codebase mapping, see the [Conceptual Model](https://ls1intum.github.io/Hephaestus/contributor/conceptual-model).

### Deployment shape

Hephaestus is a self-hosted Spring Boot application server, a TypeScript intelligence service for the conversational mentor, and a sandboxed Docker agent runner — fronted by a React webapp and Keycloak. Bring your own LLM provider; the agent sandbox and intelligence service connect independently.

## Roadmap

We schedule by semester. Items below are committed; items in [Discussions](https://github.com/ls1intum/Hephaestus/discussions) are exploratory.

**Next semester (Q3 2026)**

- **Findings dashboard for contributors.** A first-class webapp surface for the reflection channel — list, filter, contest, and respond to findings.
- **Practice-catalog editor.** Edit detection criteria and trigger events from the admin UI, not via DB migrations.

**6 months**

- **Practice-aware recognition.** Replace activity-volume scoring with indicators that reflect practice mastery and growth. Sunset legacy XP and league-points surfaces.
- **Guidance fading.** Auto-reduce guidance specificity as contributor track records improve. (Partially built — the agent already receives history.)

**12 months**

- **GitLab parity.** Close the GitHub-vs-GitLab gap on diff notes, approval comments, and label sync.
- **Broader practice catalog.** Extend beyond review and code hygiene to workflow patterns, task management, and decomposition.
- **Conversational mentor evolution.** Tighten the mentor's role in the practice-aware loop — planning and reflection sessions grounded in finding history.

## Documentation

- Technical & user docs (GitHub Pages): <https://ls1intum.github.io/Hephaestus/>
- UI component docs: [Storybook](https://main--66a8981a27ced8fef3190d41.chromatic.com/)
- Local development: [contributor guide](https://ls1intum.github.io/Hephaestus/contributor/local-development)

## Contributing

We welcome contributions from both members of our organisation and external contributors. To maintain transparency and trust:

- **Members.** Use real names and an authentic profile picture. Members can directly create branches and pull requests in this repository.
- **External contributors.** Adhere to the same identity guidelines. Contributions will only be considered if they are followed.

We adhere to best practices recommended by [GitHub's Open Source Guides](https://opensource.guide/) and the [Acceptable Use Policies](https://docs.github.com/en/site-policy/acceptable-use-policies). For full details, see [CONTRIBUTING.md](./CONTRIBUTING.md).
