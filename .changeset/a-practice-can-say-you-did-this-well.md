---
"hephaestus": minor
---

Eight defect-focused code practices can now record a clean, fully searched change as a strength instead of treating it like irrelevant work. This covers error handling, input validation, unsafe crashes, untrusted input, insecure defaults, duplication, oversized functions, and leftover debug code.

A clean result is allowed only when the practice declares an exhaustive source and the observation records the bounded corpus it searched. For code review, that claim covers the added and changed lines—not unchanged code, callers, runtime behavior, or overall correctness. If the available evidence cannot support that bounded claim, the review reports insufficient evidence rather than an unearned all-clear.
