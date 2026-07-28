---
"hephaestus": patch
---

Public pages now explain Hephaestus in plain language: it gives developers feedback on the engineering practices they use in software projects, while Heph is the separate conversational AI mentor. Pull requests, merge requests, and issues are used as current examples rather than the boundary of the product. The landing page and documentation no longer present the optional leaderboard as the main product, claim that Hephaestus replaces a human mentor, or advertise workflows that are not available.

The landing page now shows a representative practice-feedback comment instead of a scoreboard or an unshipped pull-request conversation. Shared links also include a description and social-card metadata.

The README now distinguishes today's delivery surfaces from the broader feedback loop and the planned v1.0 scope. It explains how GitHub, GitLab, Slack, and Outline contribute project context, and describes the three planned ways to receive feedback without tying them to specific page names. It also explains what pre-1.0 releases mean for self-hosted deployments, links to the live release plan, and provides clear paths to the app, documentation, Storybook, and contribution guide. Theme-aware artwork makes the feedback loop easier to scan in GitHub's light and dark modes.

The user guide now matches the shipped multi-workspace GitHub and GitLab setup, current Heph chat, practice-feedback delivery, optional leaderboard and leagues, and configurable Slack digest. Account settings now state clearly that turning off pull-request comments controls delivery only; reviews still run and findings remain available to workspace admins.

The TUM privacy statement now describes the self-service data export, consent-gated PostHog and Sentry storage, comment-delivery preference, and absence of HTTP access logs as implemented.
