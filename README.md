<div align="center">
  <img alt="Hephaestus Logo" height="100px" src="./docs/static/img/brand/hammer_bg.svg">

  [![CI/CD](https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml/badge.svg)](https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml)
  [![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/ls1intum/Hephaestus/blob/main/LICENSE)
  [![Docs](https://img.shields.io/badge/docs-live-brightgreen)](https://ls1intum.github.io/Hephaestus/)
  [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/ls1intum/Hephaestus/blob/main/CONTRIBUTING.md)
</div>

# Hephaestus — feedback on how you work

Hephaestus uses pull requests, issues, and reviews from connected GitHub and GitLab repositories to give developers feedback on their engineering practices. Each comment points to the work it is based on, explains what worked or what could improve, and suggests what to try next.

Developers decide what to do with that feedback: act on it, push back with a reason, or let it pass. Hephaestus supports mentors, teachers, and maintainers by covering routine feedback. It does not replace their judgement or the relationships they build with developers.

The core loop is to **gather** a developer's work, **observe** the practices it shows, **compose** feedback worth acting on, and **deliver** it while the work is still fresh. The developer's **response** closes the loop.

Hephaestus is built for software engineering courses, open-source projects, and teams where mentors and maintainers cannot review every contribution themselves.

The project is named after Hephaestus, the Greek god of blacksmiths and craftsmen.

## What Hephaestus does today

- **Practice feedback.** Hephaestus uses AI to review pull requests, merge requests, and issues against the engineering practices configured for a workspace. It posts the feedback as comments on the work.
- **Heph, the AI mentor.** Developers can talk with Heph about feedback and recent issues, commits, reviews, and pull or merge requests. Heph is available in the web app and, when connected, in Slack.
- **Workspaces.** Each workspace connects its own repositories, chooses its practice catalog, connects an AI model through any OpenAI-compatible endpoint, sets a monthly spending cap, and manages its members and teams.
- **Slack integration.** Developers can talk with Heph in a Slack direct message. Workspace admins can also allow Heph to use messages from selected, visibly monitored channels as context.
- **Achievements and an optional weekly leaderboard.** Workspace admins can switch on recognition features, including achievements and a leaderboard of review activity, per workspace.

## Documentation

Technical & user docs (GitHub Pages): [https://ls1intum.github.io/Hephaestus/](https://ls1intum.github.io/Hephaestus/)
UI component docs: [Storybook](https://main--66a8981a27ced8fef3190d41.chromatic.com/)

### Setup

- **Self-hosting:** follow the [install guide](https://ls1intum.github.io/Hephaestus/admin/install) ([INSTALL.md](INSTALL.md)) — one supported Docker Compose path.
- **Development:** read the [local development guide](https://ls1intum.github.io/Hephaestus/contributor/local-development) for the Spring Boot application server (with the in-process Pi mentor agent) and the React client in `webapp`.

## Contributing

Contributions are welcome. Before you start, read [CONTRIBUTING.md](./CONTRIBUTING.md). It covers the development workflow, pull request requirements, and the project's identity requirements for contributors.

The project follows a pull request contribution model and GitHub's [Acceptable Use Policies](https://docs.github.com/en/site-policy/acceptable-use-policies).
