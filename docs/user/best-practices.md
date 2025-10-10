---
id: best-practices
sidebar_position: 5
title: Best Practices for Reviewers
---

## Before the review

- Skim the linked issue to understand the motivation and acceptance criteria.
- Pull the branch locally when the diff exceeds 300 lines or touches critical paths.
- Agree on async vs. synchronous feedback windows with your team.

## During the review

- Highlight learning moments, not just defects. Tie your comment to specific principles (e.g., accessibility, test coverage).
- Use **Suggestions** within GitHub to unblock the author quickly. Hephaestus tracks adoption rate for future retrospectives.
- Capture questions or follow-ups in a mentor session while the context is fresh.

## After the review

- Log a mentoring session if the change introduced new tooling or patterns.
- Update the team's checklists when you discover a recurring smell.
- Celebrate improvements from previous retrospectives in Slack to reinforce positive behaviour.

## Timeboxing tips

1. Group reviews by context (feature, dependency upgrade) to stay in flow.
2. Keep pull requests under 400 lines where possible—split work into coherent commits.
3. If a review takes more than 45 minutes, schedule a pair-review to share cognitive load.
