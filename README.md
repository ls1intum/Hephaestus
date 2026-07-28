<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="./docs/static/img/brand/hammer_bg_dark.svg">
    <source media="(prefers-color-scheme: light)" srcset="./docs/static/img/brand/hammer_bg.svg">
    <img alt="" height="100" src="./docs/static/img/brand/hammer_bg.svg">
  </picture>

  <h1>Hephaestus</h1>
  <p><strong>Feedback on how you work</strong></p>

  <p>
    <a href="https://hephaestus.aet.cit.tum.de"><img alt="Open the Hephaestus web app" src="https://img.shields.io/badge/Open_the_web_app-493C83?style=for-the-badge"></a>
    <a href="https://ls1intum.github.io/Hephaestus/"><img alt="Read the Hephaestus documentation" src="https://img.shields.io/badge/Read_the_docs-1F75CB?style=for-the-badge&logo=docusaurus&logoColor=white"></a>
    <a href="./CONTRIBUTING.md"><img alt="Contribute to Hephaestus" src="https://img.shields.io/badge/Contribute-24292F?style=for-the-badge&logo=github&logoColor=white"></a>
  </p>

  <p>
    <a href="https://github.com/ls1intum/Hephaestus/releases/latest"><img alt="Latest Hephaestus release" src="https://img.shields.io/github/v/release/ls1intum/Hephaestus?display_name=tag&sort=semver"></a>
    <a href="https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml"><img alt="CI/CD status" src="https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml/badge.svg"></a>
    <a href="https://github.com/ls1intum/Hephaestus/blob/main/LICENSE"><img alt="MIT license" src="https://img.shields.io/badge/license-MIT-blue.svg"></a>
    <a href="https://ls1intum.github.io/Hephaestus/contributor/local-development#component-workflow-storybook--chromatic"><img alt="Set up the Hephaestus component library in Storybook" src="./docs/static/img/brand/badge-storybook.svg"></a>
  </p>
</div>

Hephaestus gives developers feedback on the engineering practices they use in software projects. It draws on evidence from tools the team already uses — including GitHub or GitLab activity, selected Slack channels, and Outline documents — to explain what worked, what could improve, and what to try next.

Developers decide what to do with that feedback: act on it, push back with a reason, or let it pass. Hephaestus supports mentors, teachers, and maintainers by covering routine feedback. It does not replace their judgement or the relationships they build with developers.

The core loop is to **gather** a developer's work, **observe** the practices it shows, **compose** feedback worth acting on, and **deliver** it where and when it is useful. The developer's **response** closes the loop.

Hephaestus is built for software engineering courses, open-source projects, and teams where mentors and maintainers cannot review every contribution themselves.

The project is named after Hephaestus, the Greek god of blacksmiths and craftsmen.

> [!IMPORTANT]
> **Hephaestus is still pre-1.0.** Until 1.0, a minor release can change configuration or APIs in ways that require action. If you self-host it, use the [latest release](https://github.com/ls1intum/Hephaestus/releases/latest), read its release notes and the [migration guide](./MIGRATION.md), and test upgrades in staging.
>
> We are aiming for a stable **v1.0.0 in mid-September 2026**. Here, stable means [predictable rules](./docs/admin/compatibility-policy.mdx) for upgrades, configuration, Docker Compose, and the REST API — not that development stops.
>
> For v1.0, the plan brings project context and feedback delivery together:
>
> - **Project context:** draw on work in GitHub and GitLab, selected Slack channels, and selected Outline documents.
> - **Alongside the work:** add feedback in GitHub or GitLab while the work is still active.
> - **In a personal view:** let developers review feedback across their work when they choose.
> - **In conversation:** let developers talk it through with Heph in the web app or Slack.
>
> Release gates take priority over the date. Follow the [v1.0 milestone](https://github.com/ls1intum/Hephaestus/milestone/5) and [release plan](https://github.com/ls1intum/Hephaestus/issues/1377) for current scope and progress.

## What Hephaestus does today

- **Practice feedback.** Hephaestus uses AI to review pull requests, merge requests, and issues against the engineering practices configured for a workspace. It posts the feedback as comments on the work.
- **Heph, the AI mentor.** Developers can talk with Heph about feedback and recent issues, commits, reviews, and pull or merge requests. Heph is available in the web app and, when connected, in Slack.
- **Project context.** Workspace admins can let Hephaestus use messages from selected, visibly monitored Slack channels and documents from selected Outline collections. Outline is a source of context; Hephaestus does not write to it.
- **Workspaces.** Each workspace connects its own repositories, chooses its practice catalog, connects an AI model through any OpenAI-compatible endpoint, sets a monthly spending cap, and manages its members and teams.

## Documentation

- **[Documentation](https://ls1intum.github.io/Hephaestus/):** user, administrator, and contributor guides.
- **[Storybook](https://ls1intum.github.io/Hephaestus/contributor/local-development#component-workflow-storybook--chromatic):** run and review web interface components locally.

### Setup

- **Self-hosting:** follow the [install guide](https://ls1intum.github.io/Hephaestus/admin/install) ([INSTALL.md](INSTALL.md)) — one supported Docker Compose path.
- **Development:** read the [local development guide](https://ls1intum.github.io/Hephaestus/contributor/local-development) for the Spring Boot application server (with the in-process Pi mentor agent) and the React client in `webapp`.

## Contributing

Contributions are welcome. Before you start, read [CONTRIBUTING.md](./CONTRIBUTING.md). It covers the development workflow, pull request requirements, and the project's identity requirements for contributors.

The project follows a pull request contribution model and GitHub's [Acceptable Use Policies](https://docs.github.com/en/site-policy/acceptable-use-policies).
