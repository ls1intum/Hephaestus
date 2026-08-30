---
title: Accessibility audit plan
description: Scope and evidence requirements for evaluating the web application against WCAG 2.2 AA.
---

# Accessibility audit plan

The audit follows [WCAG-EM](https://www.w3.org/WAI/test-evaluate/conformance/wcag-em/). Automated
checks supplement rather than replace manual evaluation.

## Scope

- **Standard:** [WCAG 2.2](https://www.w3.org/TR/WCAG22/), Levels A and AA
- **Application:** the Hephaestus SPA, including public, authentication, loading, empty, error, and
  permission-denied states
- **Complete processes:** authentication, workspace creation, developer feedback, mentor, settings,
  workspace administration, and instance administration
- **Outside the application scope:** linked external sites and identity-provider pages

Hephaestus controls and presentation around imported content remain in scope even when the content
comes from a third party.

## Evaluation matrix

| Surface or process | Representative routes and states | Manual status |
| --- | --- | --- |
| Public and legal | `/`, `/about`, `/imprint`, `/privacy`, not found | Not evaluated |
| Authentication | `/login`, workspace login, callback and error | Not evaluated |
| Workspace creation | provider selection, GitHub, GitLab, validation and errors | Not evaluated |
| Workspace home | dashboard, teams, achievements and user profiles | Not evaluated |
| Developer feedback | reviews, trace, observations, findings, delivery and targets | Not evaluated |
| Mentor | thread list, greeting, transcript, composer and copilot | Not evaluated |
| Personal settings | settings, integrations and destructive actions | Not evaluated |
| Workspace administration | members, practices, review operations, models, usage and integrations | Not evaluated |
| Instance administration | users, workspaces, audit, catalogue, providers, models and usage | Not evaluated |

For each row, record the tested revision and deployment, route or process, state, applicable WCAG
criteria, result, tester, date, and exact operating-system, browser, and assistive-technology versions.
The manual matrix must include keyboard-only operation, NVDA with Firefox on Windows, and VoiceOver
with Safari on macOS. Link every failure to an issue containing its reproduction steps, user impact,
WCAG criterion, owner, and target release.

## Automated checks

`pnpm --filter webapp run test:storybook` runs the maintained Storybook states in Chromium and treats
axe violations as errors. Record the revision, browser version, command result, and retained report
with each audit. Automated results do not establish WCAG conformance.

Until every matrix row is evaluated and each failure is fixed or linked, the public accessibility
statement must make no conformance claim.
