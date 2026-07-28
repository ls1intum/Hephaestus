---
"hephaestus": patch
---

Fixes dialogs being unusable on small screens. A dialog taller than the window had no height limit, so it hung off both edges with its title and its save button unreachable — on a 320px-wide phone the AI model form rendered 300px above the top of the screen. Dialogs now fit the window and scroll inside themselves, keeping the header, footer and close button in place. The job details panel also opened at 240px wide on a phone instead of filling the screen, and confirmation dialogs left no margin at all at 320px.

The AI usage and job screens now reflow properly down to 320px, and at 200% text zoom: wide tables scroll inside a bordered area instead of dragging the whole page sideways, and the instance usage table's expanded detail no longer opens a second horizontal scrollbar inside the first.
