<div align="center">
  <img alt="Hephaestus Logo" height="100px" src="./docs/static/img/brand/hammer_bg.svg">

  [![CI/CD](https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml/badge.svg)](https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml)
  [![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/ls1intum/Hephaestus/blob/main/LICENSE)
  [![Docs](https://img.shields.io/badge/docs-live-brightgreen)](https://ls1intum.github.io/Hephaestus/)
  [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/ls1intum/Hephaestus/blob/main/CONTRIBUTING.md)
</div>

# Hephaestus — mentoring feedback, grounded in your work

Hephaestus gives developers the mentoring feedback they would otherwise only get from a great mentor — so that every developer gets some. It reads the work a team already does on GitHub or GitLab (pull requests, issues, reviews) and tells each contributor what was done well, what could be better, and a way to get there. Developers can act on the feedback, push back with a reason, or let it pass. It does not replace mentors — it carries the routine feedback no one has time to give everyone, and it runs from the tools the team already uses.

The core loop: **gather** activity from connected repositories, **observe** engineering practices in it, **compose** feedback worth reading, **deliver** it where the work happens, and let the developer **respond**.

Hephaestus works well for university software engineering courses, open-source projects, and any team where contributors outnumber the people who can mentor them.

<img alt="Illustration of Hephaestus working alongside a software team" height="200px" src="./docs/user/img/overview/agile_hephaestus.png">

The project is named after Hephaestus, the Greek god of blacksmiths and craftsmen.

## What Hephaestus does today

- **AI practice feedback on pull requests and issues.** Hephaestus reviews activity in connected GitHub and GitLab repositories against a workspace-configurable catalogue of software engineering practices and posts feedback as comments — directly on the pull/merge request or issue.
- **Heph, your AI mentor.** A chat grounded in your actual repository activity — issues, commits, reviews, and pull requests — for questions, reflection, and next steps. Available in the web app and, when connected, in Slack.
- **Workspaces.** Each workspace connects its own repositories, chooses its practice catalogue and LLM provider, and manages members and teams through an in-app settings UI.
- **Slack integration.** Talk to Heph in Slack DMs, and optionally let the mentor use monitored team channels as context.
- **Achievements and an optional weekly leaderboard.** Workspace admins can switch on recognition features, including achievements and a leaderboard of review activity, per workspace.

## Documentation

Technical & user docs (GitHub Pages): [https://ls1intum.github.io/Hephaestus/](https://ls1intum.github.io/Hephaestus/)
UI component docs: [Storybook](https://main--66a8981a27ced8fef3190d41.chromatic.com/)

### Setup

Read the [local development guide](https://ls1intum.github.io/Hephaestus/contributor/local-development) for the Spring Boot application server (with the in-process Pi mentor agent) and the React client in `webapp`.

## Contributing

We welcome contributions from both members of our organization and external contributors. To maintain transparency and trust:

- **Members**: Must use their full real names and upload a professional and authentic profile picture. Members can directly create branches and PRs in the repository.
- **External Contributors**: Must adhere to our identity guidelines, using real names and authentic profile pictures. Contributions will only be considered if these guidelines are followed.

We adhere to best practices as recommended by [GitHub's Open Source Guides](https://opensource.guide/) and their [Acceptable Use Policies](https://docs.github.com/en/site-policy/acceptable-use-policies). Thank you for helping us create a respectful and professional environment for everyone involved.

We follow a pull request contribution model. For detailed guidelines, please refer to our [CONTRIBUTING.md](./CONTRIBUTING.md).
