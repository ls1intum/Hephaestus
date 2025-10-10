---
id: leaderboard
sidebar_position: 3
title: Leaderboard and Gamification
---

## Weekly rhythm

The leaderboard recalculates every hour and snapshots a "week in review" every Monday 00:00 CET. Historic snapshots are accessible from the dropdown in the top-right corner of the leaderboard table.

## Points model

| Activity | Base points | Notes |
| --- | --- | --- |
| Approved pull request | 15 | Bonus points for large diff coverage. |
| Change request issued | 12 | Rewards constructive feedback with actionable suggestions. |
| Pull request comments | 2 per comment | Inline code comments earn a 50% bonus. |
| Mentoring action plan completed | 25 | Counts once per sprint and requires mentor session confirmation. |

The Elo-like **League score** is separate from the weekly points. Consistent strong performance moves you up through Bronze → Silver → Gold leagues.

## Filters and drill-down

- **Repository**: Focus the leaderboard on specific repos connected to your workspace.
- **Labels**: Match reviews to course assignments (e.g., `exercise-03`).
- **Teams**: Compare squads when collaborative competitions are enabled.

Selecting a reviewer opens a drawer with:

- Pull requests they touched that week, including merge status and diff stats.
- Sentiment summary extracted from mentoring reflections.
- Quick action to copy deep links to a pull request for follow-up.

## Recognitions

Hephaestus posts the top three reviewers to Slack each Monday with direct links to their highlighted pull requests. Enable notifications in **Settings → Integrations** to receive personal digests.
