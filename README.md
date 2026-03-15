<div align="center">
  <img alt="Hephaestus Logo" height="100px" src="./docs/static/img/brand/hammer_bg.svg">

  [![CI/CD](https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml/badge.svg)](https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml)
  [![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/ls1intum/Hephaestus/blob/main/LICENSE)
  [![Docs](https://img.shields.io/badge/docs-live-brightgreen)](https://ls1intum.github.io/Hephaestus/)
  [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/ls1intum/Hephaestus/blob/main/CONTRIBUTING.md)
</div>

# Hephaestus — How You Build Matters

Hephaestus is an open-source platform that helps teams **build better practices** — not just better code. It watches how your team collaborates, spots what's working and what isn't, and coaches every contributor toward growth. Unlike dashboards built for managers, Hephaestus delivers feedback to the people doing the work, grounded in Cognitive Apprenticeship theory (Collins et al., 1989).

<img alt="Agile Hephaestus" height="200px" src="./docs/user/img/overview/agile_hephaestus.png">

Hephaestus /hɪˈfɛstəs/ is the Greek god of blacksmiths, craftsmen, and artisans, symbolizing the fusion of creativity and technical skill.

> Currently deployed for software engineering teams using GitHub, with a domain model designed for cross-domain extension.

## How It Works

```
Observe  →  Detect  →  Guide  →  Grow
```

1. **Observe**: Connect to your code platform (GitHub, GitLab) and capture how your team actually works
2. **Detect**: Spot the practices that help — and the ones that hurt — across code, process, collaboration, and learning
3. **Guide**: Coach every contributor through the right channel at the right time — AI mentor, notifications, PR comments, or achievements
4. **Grow**: Track how skills develop over time and adapt coaching as contributors improve

### Four Health Dimensions

| Dimension | What it measures | Example |
|-----------|-----------------|---------|
| **Technical** | Code and review quality | Are reviews thorough? Are bad practices declining? |
| **Process** | Workflow effectiveness | Are PRs timely? Is work-in-progress manageable? |
| **Social** | Collaboration patterns | Is review load shared? Do people collaborate across teams? |
| **Cognitive** | Understanding and growth | Are contributors building understanding — not just shipping faster? |

## Features

### Practice Detection

Analyzes how your team works — across pull requests, reviews, commits, and issues — to catch bad practices before they become habits. Early-stage work gets coaching; finished work gets rigor. Contributors stay in control: accept, dismiss, or challenge any finding.

### AI Mentor (Heph)

A conversational AI mentor grounded in your actual project activity. Heph helps contributors reflect on their work, set goals, and understand their progress — all driven by real data, not guesswork.

### Recognition & Growth

Achievements, leagues, and leaderboards make good practices visible and celebrate growth over time. Weekly digests highlight top contributors so the whole team sees what great work looks like.

### Agent Orchestration

Run AI coding agents (Claude Code, Codex, OpenCode) in sandboxed containers with configurable LLM providers, resource limits, and concurrency caps. Agents participate in the same activity stream as other contributors, receiving project context to produce better artifacts.

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
