<div align="center">
  <img alt="Hephaestus Logo" height="100px" src="./docs/static/img/brand/hammer_bg.svg">
</div>

# Hephaestus — Process-Aware Mentoring for Agile Software Teams

Hephaestus is an open-source platform for process-aware mentoring that scaffolds self-regulated learning and onboarding for agile software teams. The platform focuses on the software process — from issues to pull requests and team rituals — to bridge the theory–practice gap and help novices adopt industry best practices faster with less struggle. Heph is the platform’s conversational AI mentor that delivers repo-grounded guidance.

<img alt="Agile Hephaestus" height="200px" src="./docs/user/img/overview/agile_hephaestus.png">

Hephaestus /hɪˈfɛstəs/ is the Greek god of blacksmiths, craftsmen, and artisans, symbolizing the fusion of creativity and technical skill.

## Main Features

1. **Code Review Gamification**

   - **Weekly Leaderboard:** Stay motivated with a dynamic leaderboard that updates in real time via GitHub integration. Earn points for review activity, view detailed stats, and easily copy pull request links.

   - **Team Competitions:** Foster a collaborative spirit with team leaderboards spanning multiple repositories and options to filter the associated activities via labels.

   - **Leagues:** Engage in a structured league system where consistent review efforts build an Elo-like ranking — adding a competitive edge to your code reviews.

   - **Automated Recognition:** Celebrate excellence with weekly Slack notifications that honor the top three reviewers and link directly to the previous week's leaderboard.

2. **Heph (Conversational AI Mentor)**

   - **SRL-guided reflection:** Run structured, AI-assisted sessions that support self-regulated learning (goals → strategies → reflection) tailored to agile project work.
   - **Automated standups:** Turn weekly insights into a concise standup table to streamline team communication and accountability.
   - **Repo context awareness:** Ground guidance in actual activity (issues, commits, reviews, PRs) to deliver objective, data-informed feedback and next steps.

## Roadmap

- **Short Term:** Implement AI-based bad practices detection for pull request descriptions to ensure quality before merging by notifying the author of potential issues via email or GitHub comments.
- **Short Term:** Develop initial workspace support by moving environment variable configuration into a user-friendly workspace settings UI for setting up API credentials, webhooks, etc.
- **Short Term:** Streamline project setup and improve contributor accessibility by enhancing documentation and onboarding resources.
- **Medium Term:** Expand multi-workspace capabilities to allow configuration of multiple organizations and selected open-source repositories, enabling seamless integration into diverse GitHub projects.
- **Medium Term:** Integrate GitLab support to cater to self-hosted Git platforms, particularly for educational contexts.
- **Medium Term:** Develop an advanced mentor prompt scheduler tailored for project-based courses, enabling daily reflective sessions and guided adaptation to evolving project requirements.
- **Medium Term:** Enhance the gamification system with additional features and further expand Heph's capabilities.
- **Long Term:** Proactively integrate with GitHub and GitLab to deliver feedback directly via comments on issues or pull requests.
- **Long Term:** Launch a peer-to-peer recognition system to reward high-quality reviews and establish a review quality assurance mechanism.

## Documentation

Technical & user docs (GitHub Pages): [https://ls1intum.github.io/Hephaestus/](https://ls1intum.github.io/Hephaestus/)
UI component docs: [Storybook](https://develop--66a8981a27ced8fef3190d41.chromatic.com/)

### Setup

Read the [local development guide](https://ls1intum.github.io/Hephaestus/contributor/local-development) for server, intelligence service, and the React client in `webapp`.

#### Docker-free PostgreSQL workflow

The repository now supports running the application server against a locally managed PostgreSQL instance for environments where Docker is unavailable (for example, GitHub Copilot's Codex runtime). The `run/setup.sh` and `run/maintenance.sh` helpers are the supported entry points for provisioning that Codex environment.

1. Run `run/setup.sh` to install PostgreSQL, install npm dependencies, bootstrap Python tooling, and initialize the local database cluster.
2. On subsequent checkouts (or cached containers) invoke `run/maintenance.sh` to refresh dependencies and ensure PostgreSQL is running.
3. Database helpers (`npm run db:generate-erd-docs`, `npm run db:generate-models-intelligence-service`, etc.) automatically switch to the local database when `HEPHAESTUS_DB_MODE=local` or Docker is unavailable. You can also manage the instance manually via `scripts/local-postgres.sh [start|stop|status|restart]`.

Keycloak still requires Docker for local development; only PostgreSQL is covered by the Docker-free workflow.

## Contributing

We welcome contributions from both members of our organization and external contributors. To maintain transparency and trust:

- **Members**: Must use their full real names and upload a professional and authentic profile picture. Members can directly create branches and PRs in the repository.
- **External Contributors**: Must adhere to our identity guidelines, using real names and authentic profile pictures. Contributions will only be considered if these guidelines are followed.

We adhere to best practices as recommended by [GitHub's Open Source Guides](https://opensource.guide/) and their [Acceptable Use Policies](https://docs.github.com/en/site-policy/acceptable-use-policies). Thank you for helping us create a respectful and professional environment for everyone involved.

We follow a pull request contribution model. For detailed guidelines, please refer to our [CONTRIBUTING.md](./CONTRIBUTING.md).
