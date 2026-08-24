---
"hephaestus": minor
---

Practice reviews can now be rolled out to part of a workspace instead of all of it. Choose which
monitored repositories are reviewed and, for each of them, which base branches; choose whether
everybody's work is reviewed or only selected people's. Before a change widens either list, a preview
says how many repositories and people it would cover, so a pilot can be checked before it starts.
Sending feedback is now its own switch: pause it and reviews keep running and stay readable while
nothing reaches anybody, and resuming does not release what was held back.

Every delivery decision now keeps its reasoning. On a piece of work under Review activity, a workspace
administrator can see, for each attempt, which checks ran and in what order, which one stopped it, and
the repository, branch, author and settings it was judged against — so "why did this go quiet?" is
answerable from the screen instead of from the logs.

**Operators:** reviews now run only on work whose author is a member of the workspace, so a pull request
from an outside contributor who is not on the **Members** screen is no longer reviewed and no feedback is
prepared about them. Signing in to Hephaestus does not make somebody a member. After upgrading, read the
**People** count under Practices → Review → When and where; `MIGRATION.md` says how to cover anybody who
is missing.
