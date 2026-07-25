---
"hephaestus": minor
---

The two admin consoles now call the same thing by the same name. Both the instance console and a
workspace's own console have an **AI models**, an **AI usage** and an **Audit log** page, and the
sidebar tells you which console you are in. In a workspace, "Manage members" / "Manage teams" /
"Manage achievements" / "Manage workspace" are now just **Members**, **Teams**, **Achievements** and
**Settings**, and "Usage" is **AI usage**. Every admin page also sets a browser tab title, so an
instance tab and a workspace tab are finally distinguishable when both are open.

The instance model catalogue moved from `/admin/llm` to `/admin/models`, matching the workspace page.
The old address redirects, so existing bookmarks keep working.
