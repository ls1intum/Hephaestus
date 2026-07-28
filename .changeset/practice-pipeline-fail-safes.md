---
"hephaestus": patch
---

Hidden repositories no longer count toward practice numbers. A repository an admin marked
hidden-from-contributions still fed the developer dashboard, the mentor histograms and the area
standings, so a repository deliberately kept out of contributions kept shaping the practice picture.
Those aggregates now leave it out. A repository hidden by any one team is hidden on these views for
everyone, because they are not read in a team's context.

Dashboard counts also stop shifting between page loads. When a target was reviewed twice in the same
instant, which of the two runs counted was decided arbitrarily on each read, so the same dashboard
could show different numbers minutes apart with nothing having changed. One run is now picked
consistently.

A review with nothing to review fails instead of inventing findings. When a pull request or merge
request carried no resolvable diff — no commits yet, or the head commit could not be reached — the
review still ran, and the model reported confident observations about changes that did not exist.
Such a run now stops with a clear error and is retried, rather than publishing fiction to the author.

Workspaces created after startup get the default practice catalog. A workspace created through the UI
or API had no practices at all until the server was restarted; it is now seeded on creation, so
detection can be configured right away.
