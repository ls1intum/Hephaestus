---
"hephaestus": patch
---

Money now reads correctly across the AI usage screens: nothing spent shows as `$0` instead of `$0.000`, an amount too small to show in cents shows as `<$0.01` instead of rounding to zero, and caps drop trailing cents (`$50`, or `$49.50` when you set cents).

The AI screens now use one word per idea — "shared models" for what your host pays for and "your provider" for what you pay for — instead of nine different names for the same two things. Every cap says who set it, every pause says who can lift it and by when, and warnings arrive before the wall rather than after it. Amber warning text is also darkened so it meets contrast requirements in the light theme.

Advanced run limits in AI setup are now a proper expandable section that screen readers announce, and clearing a timeout or concurrency field shows an inline error instead of silently saving zero.
