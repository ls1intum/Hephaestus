# Leaderboard

```{contents}
:local:
:depth: 2
```

## Overview

Hephaestus features weekly leaderboards of all contributors to selected repositories of the chosen organization. Each leaderboard tracks and ranks contributors based on their code review activities using a scoring system.

## Accessing the Leaderboard

To access the leaderboard, first login to Hephaestus using your GitHub account. On successful login, you will be redirected to the leaderboard landing page. When logged in, you can always get back to the leaderboard by clicking the Hephaestus logo in the top left corner.

<!-- TODO: add example login + navigate to profile workflow here -->
<iframe height="450px" width="600px" src="https://live.rbg.tum.de/w/artemisintro/59981?video_only=1&t=0" title="Embedded Video" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>

## Understanding the Leaderboard

The main page features an overview card with your current rank and league details. In particular, you can see:
- Your rank in the currently selected leaderboard
- The time until the current leaderboard ends
- Your change in league points given the current leaderboard score and position
- Your current league and the league points required to advance to the next league via a progress bar

```{tip}
Clicking on your current rank will automatically scroll you to the your position in the leaderboard.
```

```{figure} leaderboard-overview.png
:alt: Screenshot of the Leaderboard Overview Card
:width: 100%

Screenshot of the Leaderboard Overview Card
```

The leaderboard itself consists of a table with the following columns:
- Rank
- Contributor
- Leaderboard Score
- Activity

Clicking on a row will open the contributor's profile page. It provides a more detailed view of the contributor's activity and contributions.

To get an overview of the Pull Requests a contributor has reviewed, click on the button in the `Activity` column. This will open a modal with a list of all Pull Requests the contributor has reviewed. You can further click on each Pull Request to get redirected directly to the Pull Request on GitHub. The modal also contains a "Copy"-button in the top right corner to copy all reviewed Pull Requests to your clipboard. This can be useful to quickly share a formatted summary with your team, for example for the Confluence page of your regular team meetings.

<!-- TODO: add example copy workflow here -->

## Customizing the Leaderboard

Aside from the current leaderboard, you can also review the leaderboard for previous weeks. To do so, use the dropdown menu to select a different time period. Typically, the past 4 weeks are available. The concrete time period of the currently selected leaderboard is displayed below the dropdown menu.

Additionally, the second filter option allows you to select a specific team from the chosen organization. Use this option to focus on a single team's performance and check your placement within your team.

```{note}
Please be aware that your individual ranking will only be displayed when viewing the leaderboard for a team of which you are a member. If you select a team you are not part of, your position will not appear in the rankings.
```

Lastly, it is also possible to sort the leaderboard by different criteria. The default sorting is by leaderboard score. However, you can also sort by league points, giving you a more detailed view of your progress within across the different leagues.

```{figure} leaderboard-filter.png
:alt: Screenshot of the Leaderboard Filter Options

Screenshot of the Leaderboard Filter Options
```

<!-- TODO: admin part of the leaderboard -->