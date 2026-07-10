<div align="center">
  <img alt="Hephaestus Logo" height="100px" src="./docs/static/img/brand/hammer_bg.svg">

  [![CI/CD](https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml/badge.svg)](https://github.com/ls1intum/Hephaestus/actions/workflows/cicd.yml)
  [![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/ls1intum/Hephaestus/blob/main/LICENSE)
  [![Docs](https://img.shields.io/badge/docs-live-brightgreen)](https://ls1intum.github.io/Hephaestus/)
  [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/ls1intum/Hephaestus/blob/main/CONTRIBUTING.md)
</div>

# Hephaestus — Process-Aware Mentoring for Agile Software Teams

Hephaestus is an open-source platform for process-aware mentoring that scaffolds self-regulated learning and onboarding for agile software teams. The platform focuses on the software process — from issues to pull requests and team rituals — to bridge the theory–practice gap and help novices adopt industry best practices faster with less struggle. Heph is the platform’s conversational AI mentor that delivers repo-grounded guidance.

<img alt="Agile Hephaestus" height="200px" src="./docs/user/img/overview/agile_hephaestus.png">

Hephaestus /hɪˈfɛstəs/ is the Greek god of blacksmiths, craftsmen, and artisans, symbolizing the fusion of creativity and technical skill.

## Main Features

1. **Practice-Focused Reflection**

   - **My Practices:** Reflect on your own review habits with criterion-referenced practice cards tied to your review cycle. Nothing is ranked or scored against your peers.

   - **Practice Overview:** Give mentors an audited, k-anonymised view of cohort health and a needs-attention roster — no ranking, no leaderboard — with a per-developer drill-down that records every access.

   - **Activity Monitor:** Follow an append-only, idempotent activity event log that surfaces a transparent record of your contributions.

   - **Achievements:** Celebrate consistent contributions and milestones that recognize healthy habits without competition.

2. **Heph (Conversational AI Mentor)**

   - **SRL-guided reflection:** Run structured, AI-assisted sessions that support self-regulated learning (goals → strategies → reflection) tailored to agile project work.
   - **Automated standups:** Turn weekly insights into a concise standup table to streamline team communication and accountability.
   - **Repo context awareness:** Ground guidance in actual activity (issues, commits, reviews, PRs) to deliver objective, data-informed feedback and next steps.

## Roadmap

- **Short Term:** Develop initial workspace support by moving environment variable configuration into a user-friendly workspace settings UI for setting up API credentials, webhooks, etc.
- **Short Term:** Streamline project setup and improve contributor accessibility by enhancing documentation and onboarding resources.
- **Medium Term:** Expand multi-workspace capabilities to allow configuration of multiple organizations and selected open-source repositories, enabling seamless integration into diverse GitHub projects.
- **Medium Term:** Integrate GitLab support to cater to self-hosted Git platforms, particularly for educational contexts.
- **Medium Term:** Develop an advanced mentor prompt scheduler tailored for project-based courses, enabling daily reflective sessions and guided adaptation to evolving project requirements.
- **Long Term:** Proactively integrate with GitHub and GitLab to deliver feedback directly via comments on issues or pull requests.
- **Long Term:** Launch a peer-to-peer recognition system to reward high-quality reviews and establish a review quality assurance mechanism.

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
