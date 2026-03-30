<div align="center">
  <img alt="Hephaestus Logo" height="100px" src="./docs/static/img/brand/hammer_bg.svg">

  [![CI/CD](https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml/badge.svg)](https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml)
  [![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/ls1intum/Hephaestus/blob/main/LICENSE)
  [![Docs](https://img.shields.io/badge/docs-live-brightgreen)](https://ls1intum.github.io/Hephaestus/)
  [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/ls1intum/Hephaestus/blob/main/CONTRIBUTING.md)
</div>

# Hephaestus — Practice-Aware Guidance for Software Projects

Hephaestus is an open-source platform for **practice-aware guidance**. You define the practices that matter for your project, and Hephaestus evaluates every contribution against them. When something needs attention, it tells the contributor directly, with guidance that adapts based on their track record.

<img alt="Hephaestus mascot" height="200px" src="./docs/user/img/overview/agile_hephaestus.png">

> Currently integrates with GitHub and GitLab.

## How It Works

1. **Define practices** — Admins curate a practice catalog per workspace (e.g., contribution description quality, review thoroughness, documentation standards). Each practice has a detection prompt that tells the AI what to look for.

2. **Evaluate contributions** — When a contribution is submitted or updated, an AI agent runs in a sandboxed container with full project access. It evaluates the contribution against each relevant practice and produces structured findings with a verdict, severity, evidence, and tailored guidance.

3. **Adapt guidance** — The system tracks each contributor's history per practice and instructs the agent to adapt accordingly. New contributors get concrete examples. Repeat issues get direct coaching. Improving contributors get prompts for reflection.

4. **Deliver feedback** — Findings appear where the work happens. Contributors can mark them as applied, disputed, or not applicable.

### Four Health Dimensions

We propose organizing practices into four dimensions so no single area dominates:

| Dimension | What it covers | Example question |
|-----------|---------------|-----------------|
| **Technical** | Contribution and review quality | Are reviews substantive? Are known anti-patterns declining? |
| **Process** | Workflow effectiveness | Are contributions well-scoped? Is work-in-progress manageable? |
| **Social** | Collaboration patterns | Is review load shared? Do people work across team boundaries? |
| **Cognitive** | Understanding and growth | Are contributors building understanding, not just shipping faster? |

## Features

### Practice Detection

The core of the platform. An AI agent evaluates each contribution against your workspace's practice catalog and produces structured findings — with guidance adapted to each contributor's experience level.

Findings appear as PR comments and inline code annotations — contextual coaching where the work happens. Contributors stay in control: mark any finding as applied, disputed, or not applicable.

### AI Mentor (Heph)

A conversational AI mentor grounded in your actual project activity. Heph helps contributors reflect on their work, set goals, and plan next steps. Generates session summaries that can be shared with mentors.

### Engagement & Recognition

Leaderboards, leagues, and achievements make good practices visible across the team. Weekly Slack digests highlight standout contributors.

### Agent Orchestration

Run AI agents (Claude Code, OpenCode) in sandboxed Docker containers with configurable LLM providers, resource limits, and concurrency caps.

## Roadmap

- **Practice Detection**: Broaden coverage to review depth, workflow patterns, and task management
- **Good Practice Recognition**: Detect and reinforce beneficial patterns, not just flag problems
- **Project Health**: Track practice adherence across multiple dimensions per project
- **Guidance Fading**: Reduce guidance automatically as contributors improve over time (partially implemented — agent already receives contributor history per practice)
- **Cross-Platform**: Full parity across GitHub, GitLab, and other code platforms
- **Beyond Software**: Explore applicability to design, research, and course projects

## Documentation

Technical & user docs (GitHub Pages): [https://ls1intum.github.io/Hephaestus/](https://ls1intum.github.io/Hephaestus/)
UI component docs: [Storybook](https://main--66a8981a27ced8fef3190d41.chromatic.com/)

### Domain Model

<img alt="Hephaestus Domain Model" src="./docs/diagrams/domain-model.svg" width="800">

### Architecture

<img alt="Hephaestus Architecture" src="./docs/diagrams/architecture.svg" width="800">

For theoretical foundations and codebase mapping, see the [Conceptual Model](https://ls1intum.github.io/Hephaestus/contributor/conceptual-model).

### Setup

Read the [local development guide](https://ls1intum.github.io/Hephaestus/contributor/local-development) for server, intelligence service, and the React client in `webapp`.

## Contributing

We welcome contributions from both members of our organization and external contributors. To maintain transparency and trust:

- **Members**: Must use their full real names and upload a professional and authentic profile picture. Members can directly create branches and PRs in the repository.
- **External Contributors**: Must adhere to our identity guidelines, using real names and authentic profile pictures. Contributions will only be considered if these guidelines are followed.

We adhere to best practices as recommended by [GitHub's Open Source Guides](https://opensource.guide/) and their [Acceptable Use Policies](https://docs.github.com/en/site-policy/acceptable-use-policies). Thank you for helping us create a respectful and professional environment for everyone involved.

We follow a pull request contribution model. For detailed guidelines, please refer to our [CONTRIBUTING.md](./CONTRIBUTING.md).
