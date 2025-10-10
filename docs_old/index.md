# Hephaestus — Process-Aware Mentoring for Agile Software Teams

```{figure} ./images/agile_hephaestus.png
:width: 200px
Hephaestus /hɪˈfɛstəs/ is the Greek god of blacksmiths, craftsmen, and artisans, symbolizing the fusion of creativity and technical skill.
```

## Main Features

1. **Code Review Gamification**
    - **Weekly Leaderboard:** Stay motivated with a dynamic leaderboard that updates in real time via GitHub integration. Earn points for review activity, view detailed stats, and easily copy pull request links.

    - **Team Competitions:** Foster a collaborative spirit with team leaderboards spanning multiple repositories and options to filter the associated activities via labels.

    - **Leagues:** Engage in a structured league system where consistent review efforts build an Elo-like ranking — adding a competitive edge to your code reviews.

    - **Automated Recognition:** Celebrate excellence with weekly Slack notifications that honor the top three reviewers and link directly to the previous week's leaderboard.

2. **Heph (Conversational AI Mentor)**
    - **SRL-guided reflection:** Engage in AI-assisted sessions that support goal setting, strategy selection, and reflection aligned with agile rituals.
    - **Automated standups:** Convert insights into a structured weekly standup table for streamlined team communication.
    - **Repo context awareness:** Use GitHub activity to ground mentoring in issues, commits, reviews, and pull requests for objective, data-informed feedback.

Note: Hephaestus is the platform; Heph is the conversational AI mentor embedded in the platform.

```{toctree}
:caption: User Guide
:includehidden:
:maxdepth: 2

user/leaderboard/index
user/workspace/index
user/ai_mentor
user/best_practices/index

```

```{toctree}
:caption: Contributor Guide
:includehidden:
:maxdepth: 2

dev/release_management
dev/setup_guide
dev/getting_started/index
dev/system_design/index
dev/coding_design_guidelines/index
dev/database/schema
dev/database/migration
dev/mail_notifications/index

```

```{toctree}
:caption: Administrator Guide
:includehidden:
:maxdepth: 2

admin/production_setup
```
