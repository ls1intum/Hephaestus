<div align="center">
  <img alt="Hephaestus Logo" height="100px" src="./docs/images/hammer_bg.svg">
</div>

# Hephaestus: Forging Healthy Software Teams

Our mission is simple: to empower novice software engineers and foster sustainable, collaborative development practices. Hephaestus is a growing platform designed to provide actionable tools — like code review gamification and AI-guided reflective sessions — that help teams continuously improve and stay focused on real-world challenges. While we're just getting started, our vision is to evolve into a comprehensive framework that nurtures agile, effective, and healthy software teams for the long term.

<img alt="Agile Hephaestus" height="200px" src="./docs/images/agile_hephaestus.png">

Hephaestus /hɪˈfɛstəs/ is the Greek god of blacksmiths, craftsmen, and artisans, symbolizing the fusion of creativity and technical skill.

## Main Features

1. **Code Review Gamification**
    - **Weekly Leaderboard:** Stay motivated with a dynamic leaderboard that updates in real time via GitHub integration. Earn points for review activity, view detailed stats, and easily copy pull request links.

    - **Team Competitions:** Foster a collaborative spirit with team leaderboards spanning multiple repositories and options to filter the associated activities via labels.

    - **Leagues:** Engage in a structured league system where consistent review efforts build an Elo-like ranking — adding a competitive edge to your code reviews.

    - **Automated Recognition:** Celebrate excellence with weekly Slack notifications that honor the top three reviewers and link directly to the previous week's leaderboard.

2. **AI Mentor**
    - **Reflective Sessions:** Engage in AI-assisted weekly sessions that help you set, adjust, and achieve your goals through structured reflection.
    - **Automated Standups:** Convert insights from your reflective sessions into a structured weekly standup table for streamlined team communication.
    - **GitHub Activity Awareness:** Utilize the GitHub integration to provide context during reflective sessions, driving continuous improvement through objective, data-driven feedback.

## Roadmap

- **Short Term:** Implement AI-based bad practices detection for pull request descriptions to ensure quality before merging by notifying the author of potential issues via email or GitHub comments.
- **Short Term:** Develop initial workspace support by moving environment variable configuration into a user-friendly workspace settings UI for setting up API credentials, webhooks, etc.
- **Short Term:** Streamline project setup and improve contributor accessibility by enhancing documentation and onboarding resources.
- **Medium Term:** Expand multi-workspace capabilities to allow configuration of multiple organizations and selected open-source repositories, enabling seamless integration into diverse GitHub projects.
- **Medium Term:** Integrate GitLab support to cater to self-hosted Git platforms, particularly for educational contexts.
- **Medium Term:** Develop an advanced mentor prompt scheduler tailored for project-based courses, enabling daily reflective sessions and guided adaptation to evolving project requirements.
- **Medium Term:** Enhance the gamification system with additional features and further expand the AI Mentor's capabilities.
- **Long Term:** Proactively integrate with GitHub and GitLab to deliver feedback directly via comments on issues or pull requests.
- **Long Term:** Launch an peer-to-peer recognition system to reward high-quality reviews and establish a review quality assurance mechanism.

## Documentation

Technical/user docs: [Read the Docs](https://ls1intum.github.io/Hephaestus/)  
UI component docs: [Storybook](https://develop--66a8981a27ced8fef3190d41.chromatic.com/)

### Setup

Read the [setup guide](https://ls1intum.github.io/Hephaestus/dev/setup_guide) for server, intelligence service, and the React client in `webapp-react`.

## Contributing

We welcome contributions from both members of our organization and external contributors. To maintain transparency and trust:

- **Members**: Must use their full real names and upload a professional and authentic profile picture. Members can directly create branches and PRs in the repository.
- **External Contributors**: Must adhere to our identity guidelines, using real names and authentic profile pictures. Contributions will only be considered if these guidelines are followed.

We adhere to best practices as recommended by [GitHub's Open Source Guides](https://opensource.guide/) and their [Acceptable Use Policies](https://docs.github.com/en/site-policy/acceptable-use-policies). Thank you for helping us create a respectful and professional environment for everyone involved.

We follow a pull request contribution model. For detailed guidelines, please refer to our [CONTRIBUTING.md](./CONTRIBUTING.md).
