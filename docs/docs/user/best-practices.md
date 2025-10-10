---
title: Best Practices
sidebar_position: 4
description: Detect, review, and resolve coding best practices surfaced by Hephaestus.
---

## Overview

Hephaestus provides the Best practices page to help novice developers learn about the best practices in software development.
The page displays a list of all assigned and open pull requests, along with detected good and bad practices.
Detection can be triggered by users or is scheduled automatically on lifecycle events of pull requests.

## Getting Started

The Best practices page is accessible from the main menu of Hephaestus.
It displays a current summary, all assigned and open pull requests, and a practice legend.

![Best practices page](./best-practices/best-practices.png "Best practices page")

## Usage
The Best practices page uses the most recent state of pull requests.
Using the "Analyze Changes" button, users can trigger detection on the pull request.
Detected practices are displayed under the "Current analysis" section.

![Analysis of one pull request](./best-practices/bad-practices.png "Analysis of one pull request")

For each pull request we display a summary of the current practice state.
Every practice has a title, description, severity, and state.
Users can use the "Resolve" button to adapt the state to fixed, won't fix, or wrong.
They can also provide feedback on the practice, which is sent to the Hephaestus team for further improvement.

![Resolve button](./best-practices/resolve.png "Resolve button")

## Automated Detection
Hephaestus automatically schedules detection on pull request lifecycle events, like creation, ready to review, ready to merge, and merged.
If any bad practices are detected, Hephaestus sends a mail notification to the user.
Users can manage their mail notification settings in the user settings.

![Mail notification settings](./best-practices/settings.png "Mail notification settings")