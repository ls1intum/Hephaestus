<div align="center">
  <img alt="Hephaestus Logo" height="100px" src="./docs/static/img/brand/hammer_bg.svg">

  [![CI/CD](https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml/badge.svg)](https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml)
  [![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/ls1intum/Hephaestus/blob/main/LICENSE)
  [![Docs](https://img.shields.io/badge/docs-live-brightgreen)](https://ls1intum.github.io/Hephaestus/)
  [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/ls1intum/Hephaestus/blob/main/CONTRIBUTING.md)
</div>

# Hephaestus — How You Build Matters

Hephaestus is an open-source platform for **practice-aware guidance**. You define the practices that matter for your project — what good collaboration looks like — and Hephaestus evaluates every contribution against them. When something needs attention, it tells the contributor directly, adapting its guidance based on their track record. Grounded in Cognitive Apprenticeship theory (Collins et al., 1989).

<img alt="Agile Hephaestus" height="200px" src="./docs/user/img/overview/agile_hephaestus.png">

Hephaestus /hɪˈfɛstəs/ is the Greek god of blacksmiths, craftsmen, and artisans, symbolizing the fusion of creativity and technical skill.

> Currently deployed for software engineering teams using GitHub and GitLab, with a domain model designed for cross-domain extension.

## How It Works

1. **Define practices** — Admins curate a catalog of engineering practices per workspace (e.g., PR description quality, review thoroughness, error handling standards). Each practice has a detection prompt that tells the AI what to look for.

2. **Evaluate contributions** — When a PR is opened or updated, Hephaestus runs an AI agent in a sandboxed container with full repository access. The agent evaluates the contribution against each relevant practice and produces structured findings with a verdict (positive, negative, or not applicable), severity, and tailored guidance.

3. **Adapt guidance** — The system tracks each contributor's history per practice. New contributors get concrete examples (modeling). Repeat issues get direct coaching. Contributors who've improved get prompts for reflection. This follows Cognitive Apprenticeship: guidance fades as competence grows.

4. **Deliver feedback** — Findings appear as PR comments and inline code annotations, so feedback lives where the work happens. Contributors can mark findings as applied, disputed, or not applicable.

### Four Health Dimensions

Practices are organized into four dimensions so no single area dominates:

| Dimension | What it covers | Example question |
|-----------|---------------|-----------------|
| **Technical** | Code and review quality | Are reviews substantive? Are known anti-patterns declining? |
| **Process** | Workflow effectiveness | Are PRs well-scoped? Is work-in-progress manageable? |
| **Social** | Collaboration patterns | Is review load shared? Do people work across team boundaries? |
| **Cognitive** | Understanding and growth | Are contributors building understanding, not just shipping faster? |

## Features

### Practice Detection

The core of the platform. An AI agent evaluates each contribution against your workspace's practice catalog. Findings include a verdict, severity, evidence, and guidance text — each tagged with a Cognitive Apprenticeship method (modeling, coaching, scaffolding, articulation, reflection, or exploration) selected based on the contributor's history with that practice.

Contributors stay in control: mark any finding as applied, disputed, or not applicable.

### AI Mentor (Heph)

A conversational AI mentor grounded in your actual project activity. Heph helps contributors reflect on their work, set goals, and plan next steps. Generates shareable session summaries.

### Engagement & Recognition

Leaderboards, leagues, and achievements make good practices visible across the team. Weekly Slack digests highlight standout contributors.

### Agent Orchestration

Run AI coding agents (Claude Code, Codex, OpenCode) in sandboxed Docker containers with configurable LLM providers, resource limits, and concurrency caps. Agents participate in the same activity stream as other contributors.

## Domain Model

Eight domain-independent concepts form the analytical pipeline — **Observe → Detect → Guide → Grow**:

```
Participant → performs → Activity → on → Artifact
                              ↓
                     aggregates into → Signal ← detects ← Practice
                                                              ↓
                                                    informs → Guidance → shapes → Trajectory
```

| Concept | What it represents | Software engineering example |
|---------|-------------------|---------------------------|
| **Project** | Scope of observation | GitHub organization or course workspace |
| **Participant** | Person contributing to the project | Developer, teaching assistant |
| **Artifact** | Tangible output of collaboration | PR, review, issue, commit |
| **Activity** | Observable event in the project | Review submitted, PR opened, comment posted |
| **Practice** | A standard or pattern to evaluate | "PRs should have descriptive titles and context" |
| **Signal** | Aggregated measure derived from activity | Review count per week, average PR size |
| **Guidance** | Feedback delivered to a participant | AI mentor session, PR comment, achievement unlock |
| **Trajectory** | A participant's development arc over time | Solo contributor → collaborative practitioner |

## Roadmap

- **Practice Detection**: Broaden practice coverage to include review depth, commit patterns, and issue management
- **Good Practice Recognition**: Detect and reinforce beneficial patterns, not just flag anti-patterns
- **PR Comments**: Inline coaching feedback directly on pull requests *(shipped)*
- **Health Signals**: Explicit multi-dimensional health model with confidence scores
- **Trajectories**: Track developmental arcs with phase detection and adaptive fading
- **GitLab Integration**: Full parity with GitHub for self-hosted educational deployments
- **Generalization**: Validate the framework beyond software engineering (design, research, course projects)

## Documentation

Technical & user docs (GitHub Pages): [https://ls1intum.github.io/Hephaestus/](https://ls1intum.github.io/Hephaestus/)
UI component docs: [Storybook](https://main--66a8981a27ced8fef3190d41.chromatic.com/)

### Setup

Read the [local development guide](https://ls1intum.github.io/Hephaestus/contributor/local-development) for server, intelligence service, and the React client in `webapp`.

## Contributing

We welcome contributions from both members of our organization and external contributors. To maintain transparency and trust:

- **Members**: Must use their full real names and upload a professional and authentic profile picture. Members can directly create branches and PRs in the repository.
- **External Contributors**: Must adhere to our identity guidelines, using real names and authentic profile pictures. Contributions will only be considered if these guidelines are followed.

We adhere to best practices as recommended by [GitHub's Open Source Guides](https://opensource.guide/) and their [Acceptable Use Policies](https://docs.github.com/en/site-policy/acceptable-use-policies). Thank you for helping us create a respectful and professional environment for everyone involved.

We follow a pull request contribution model. For detailed guidelines, please refer to our [CONTRIBUTING.md](./CONTRIBUTING.md).
