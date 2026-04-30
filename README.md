<div align="center">
  <img alt="Hephaestus Logo" height="100px" src="./docs/static/img/brand/hammer_bg.svg">

  [![CI/CD](https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml/badge.svg)](https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml)
  [![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/ls1intum/Hephaestus/blob/main/LICENSE)
  [![Docs](https://img.shields.io/badge/docs-live-brightgreen)](https://ls1intum.github.io/Hephaestus/)
  [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/ls1intum/Hephaestus/blob/main/CONTRIBUTING.md)
</div>

# Hephaestus — Practice-Aware Guidance for Software Projects

Hephaestus is an open-source platform for **practice-aware guidance**. You define the practices that matter for your project; Hephaestus evaluates every contribution against them and coaches each contributor directly, with guidance that adapts based on their track record.

<img alt="Hephaestus mascot" height="200px" src="./docs/user/img/overview/agile_hephaestus.png">

> Integrates with GitHub (primary) and GitLab (webhook ingestion, practice detection). Full platform parity is on the roadmap.

## How It Works

1. **Define practices** — Admins curate a practice catalog per workspace (e.g., contribution description quality, review thoroughness, documentation standards). Each practice has a detection prompt that tells the AI what to look for.

2. **Evaluate contributions** — When a contribution is submitted or updated, an AI agent runs in a sandboxed container with full project access. It evaluates the contribution against each relevant practice and produces structured findings with a verdict, severity, evidence, and tailored guidance.

3. **Adapt guidance** — The system tracks each contributor's history per practice and instructs the agent to adapt accordingly. New contributors get concrete examples. Repeat issues get direct coaching. Improving contributors get prompts for reflection.

4. **Deliver feedback** — Findings appear where the work happens. Contributors can mark them as applied, disputed, or not applicable.

## Features

### Practice Detection

An AI agent evaluates each contribution against your workspace's practice catalog and produces structured findings: verdict, severity, evidence, guidance. Findings appear as PR comments and inline code annotations. Contributors stay in control — mark any finding as applied, disputed, or not applicable.

### Adaptive Coaching

Guidance adapts to each contributor's track record per practice — concrete examples for new contributors, direct coaching for repeat issues, reflection prompts as people improve. **Heph**, the conversational AI mentor, complements in-context findings with goal-setting and reflection grounded in actual project activity.

### Engagement & Recognition

A weekly leaderboard, leagues, and achievements surface contribution activity over time. Today these track activity volume; deepening recognition to reflect practice mastery is on the roadmap.

## Implementation notes

AI agents (Claude Code, OpenCode) run in sandboxed Docker containers with configurable LLM providers, resource limits, and concurrency caps. Practice detection, agent orchestration, and delivery are independently testable — see the [Conceptual Model](https://ls1intum.github.io/Hephaestus/contributor/conceptual-model) for the architecture.

## Roadmap

- **Practice-aware recognition** — replace activity-shaped scoring with indicators that reflect practice mastery and growth.
- **Broader practice catalog** — expand beyond review and code hygiene to workflow patterns and task management.
- **Cross-platform parity** — close the GitHub/GitLab gap; today GitLab covers webhook ingestion and practice detection.
- **Guidance fading** — reduce guidance automatically as contributors improve (partially implemented; agent already receives contributor history per practice).

## Documentation

Technical & user docs (GitHub Pages): [https://ls1intum.github.io/Hephaestus/](https://ls1intum.github.io/Hephaestus/)
UI component docs: [Storybook](https://main--66a8981a27ced8fef3190d41.chromatic.com/)

### Domain Model

<img alt="Hephaestus Domain Model" src="./docs/diagrams/domain-model.svg" width="800">

### Architecture

<img alt="Hephaestus Architecture" src="./docs/diagrams/architecture.svg" width="800">

For domain entities and codebase mapping, see the [Conceptual Model](https://ls1intum.github.io/Hephaestus/contributor/conceptual-model).

### Setup

Read the [local development guide](https://ls1intum.github.io/Hephaestus/contributor/local-development) for server, intelligence service, and the React client in `webapp`.

## Contributing

We welcome contributions from both members of our organization and external contributors. To maintain transparency and trust:

- **Members**: Must use their full real names and upload a professional and authentic profile picture. Members can directly create branches and PRs in the repository.
- **External Contributors**: Must adhere to our identity guidelines, using real names and authentic profile pictures. Contributions will only be considered if these guidelines are followed.

We adhere to best practices as recommended by [GitHub's Open Source Guides](https://opensource.guide/) and their [Acceptable Use Policies](https://docs.github.com/en/site-policy/acceptable-use-policies). Thank you for helping us create a respectful and professional environment for everyone involved.

We follow a pull request contribution model. For detailed guidelines, please refer to our [CONTRIBUTING.md](./CONTRIBUTING.md).
