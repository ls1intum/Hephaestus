<div align="center">
  <img alt="Hephaestus Logo" height="100px" src="./docs/static/img/brand/hammer_bg.svg">

  [![CI/CD](https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml/badge.svg)](https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml)
  [![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/ls1intum/Hephaestus/blob/main/LICENSE)
  [![Docs](https://img.shields.io/badge/docs-live-brightgreen)](https://ls1intum.github.io/Hephaestus/)
  [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/ls1intum/Hephaestus/blob/main/CONTRIBUTING.md)
</div>

# Hephaestus — How You Build Matters

Hephaestus is an open-source platform for **formative practice analytics** in project-based work. It observes how participants collaborate — from issues to pull requests to code reviews — detects the practices that drive quality, and guides every contributor — human and AI — toward growth. Grounded in Cognitive Apprenticeship theory (Collins et al., 1989), Hephaestus bridges the gap between summative project metrics (built for managers) and formative feedback (built for the people doing the work).

<img alt="Agile Hephaestus" height="200px" src="./docs/user/img/overview/agile_hephaestus.png">

Hephaestus /hɪˈfɛstəs/ is the Greek god of blacksmiths, craftsmen, and artisans, symbolizing the fusion of creativity and technical skill.

> Currently deployed for software engineering teams using GitHub, with a domain model designed for cross-domain extension.

## How It Works

```
Observe  →  Detect  →  Guide  →  Grow
```

1. **Observe**: Ingest development activity from code platforms (GitHub, GitLab) as an immutable activity event ledger
2. **Detect**: Identify behavioral practices — both beneficial and detrimental — across four health dimensions
3. **Guide**: Deliver formative feedback through the right channel — AI mentor, notifications, achievements, or directly at the point of work
4. **Grow**: Track participant trajectories to adapt coaching intensity as competence develops

### Four Health Dimensions

| Dimension | What it measures | Example |
|-----------|-----------------|---------|
| **Technical** | Domain skill quality | Review thoroughness, bad practice rate |
| **Process** | Workflow effectiveness | Lead time, WIP count, PR abandonment |
| **Social** | Collaboration quality | Review reciprocity, cross-team engagement |
| **Cognitive** | Learning & self-regulation | Declining bad practice rate, reflection depth |

## Features

### Practice Detection

AI-powered analysis of pull requests that identifies anti-patterns (missing descriptions, oversized changes, incomplete templates) with lifecycle-aware severity — draft PRs receive gentler feedback, ready-to-merge PRs receive stricter review. Contributors can mark findings as fixed, won't fix, or incorrect, closing the feedback loop.

### AI Mentor (Heph)

A conversational AI mentor grounded in your actual project activity. Heph guides structured reflection — goal-setting, progress review, and self-assessment — generates weekly progress summaries, and surfaces relevant pull requests and action items — all driven by real data, not guesswork.

### Recognition & Growth

Achievement progression chains (60+ across five categories, common to mythic rarity), an Elo-like league system for persistent ranking, and weekly leaderboards make good practices visible and track skill development over time. Automated Slack digests recognize top contributors each week.

### Agent Orchestration

Run AI coding agents (Claude Code, Codex, OpenCode) in sandboxed containers with configurable LLM providers, resource limits, and concurrency caps. Agents participate in the same activity stream as human contributors.

## Domain Model

Eight domain-independent concepts form the analytical pipeline — **Observe → Detect → Guide → Grow**:

```
Participant → performs → Activity → on → Artifact
                              ↓
                     aggregates into → Signal ← detects ← Practice
                              ↓                               ↓
                   triggers → Guidance → targets → Participant
                              ↓
                        feeds → Trajectory ← tracked over ← Practice

All scoped to: Project
```

| Concept | Description | Software instance |
|---------|-------------|-------------------|
| **Project** | Bounded endeavor with goals and norms | Workspace / Repository |
| **Participant** | Person or agent developing practice | Developer, Bot |
| **Artifact** | Tangible work product | PR, Review, Issue, Comment |
| **Activity** | Immutable record of what happened | ActivityEvent |
| **Practice** | Named behavioral pattern | "Gives substantive reviews" |
| **Signal** | Quantified health measure along a dimension | Review thoroughness: 0.85 |
| **Guidance** | Coaching response grounded in a CA method | PR comment, mentor prompt |
| **Trajectory** | Developmental arc over time | Improving social health |

See the full [Conceptual Model](https://ls1intum.github.io/Hephaestus/contributor/conceptual-model) for cross-domain instantiation, architecture diagrams, and the Cognitive Apprenticeship mapping.

## Architecture

```
┌──────────────────────────────────────────────────────┐
│  Hephaestus Platform                                 │
│                                                      │
│  Web App          Application Server   Webhook Ingest│
│  (React 19)  ───▶ (Spring Boot 3.5) ◀── (Hono)     │
│       │                  │                    ▲      │
│       ▼                  ▼                    │      │
│  Intelligence Service (Hono · LangGraph)      │      │
└───────────────────────┬───────────────────────┘      │
                        │                              │
    ┌───────────────────┼──────────────────┐           │
    ▼                   ▼                  ▼           │
 Data Infra       Platform Services    Code Platforms──┘
 PostgreSQL       Keycloak · Slack     GitHub · GitLab
 NATS             LLM · Langfuse
```

## Roadmap

- **Practice Detection**: Expand beyond PR descriptions to review quality, commit patterns, and issue management
- **PR Comments**: Post coaching feedback directly on pull requests (CA coaching at point of work)
- **Good Practice Recognition**: Detect and reinforce beneficial patterns, not just flag anti-patterns
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
